package org.axway.grapes.jenkins;

import hudson.Extension;
import hudson.maven.AbstractMavenProject;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.Action;
import hudson.model.ManagementLink;
import hudson.security.Permission;
import jenkins.model.Jenkins;
import org.axway.grapes.jenkins.config.GrapesConfig;
import org.axway.grapes.jenkins.notifications.NotificationHandler;
import org.axway.grapes.jenkins.resend.ResendBuildAction;
import org.axway.grapes.jenkins.resend.ResendProjectAction;
import org.kohsuke.stapler.HttpRedirect;
import org.kohsuke.stapler.HttpResponse;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;

/**
 * Resend Root Action
 *
 * <p>Manage the Grapes notification that failed.</p>
 *
 * @author jdcoffre
 */
@Extension
public class AdministrateResendAction extends ManagementLink {

    private static final String ROOT_ACTION_ICON = "img/resend-icon.png";

    private List<ResendProjectAction> resendActions = new ArrayList<ResendProjectAction>();

    @Override
    public String getIconFileName() {
        return GrapesPlugin.getPluginResourcePath() + ROOT_ACTION_ICON;
    }

    public String getDisplayName() {
        return "Manage Grapes Notifications";
    }

    @Override
    public String getUrlName() {
        return "grapes";
    }

    @Override
    public Permission getRequiredPermission() {
        return Jenkins.ADMINISTER;
    }

    /**
     * Refresh the resend actions
     */
    public void refresh(){
        resendActions.clear();
        for(AbstractMavenProject mavenProject: Jenkins.getInstance().getAllItems(AbstractMavenProject.class)){
            final ResendProjectAction resendProjectAction = getAllResendActions(mavenProject);
            if(resendProjectAction != null){
                resendActions.add(resendProjectAction);
            }
        }
    }

    /**
     * Returns a Map that contains as keys, the names of modules to resend, as value, the versions to resend
     *
     * @return Map<String, String>
     */
    public Map<String, String> getModules(){
        refresh();
        final Map<String, String> modulesInfo = new HashMap<String, String>();
        for(ResendProjectAction action: resendActions){
            modulesInfo.putAll(action.getModulesInfo());
        }

        return modulesInfo;
    }

    /**
     *  Performs the notification on 'POST' action named "perform"
     *
     * @param req StaplerRequest
     * @param rsp StaplerResponse
     * @return HttpResponse
     */
    public HttpResponse do_perform(final StaplerRequest req, final StaplerResponse rsp)  {
        // Only administrator can create a new site.
        Jenkins.getInstance().checkPermission(Jenkins.ADMINISTER);

        // TODO: display the progression

        for(ResendProjectAction resendProjectAction: resendActions){
            try {
                final NotificationHandler notifHandler = new NotificationHandler(resendProjectAction.getConfig());
                for(Map.Entry<AbstractBuild<?,?>, List<ResendBuildAction>> resendBuildAction: resendProjectAction.getResendActionPerBuild().entrySet()){
                    notifHandler.send(resendBuildAction.getKey(), resendBuildAction.getValue());
                }

            } catch (Exception e ){
                GrapesPlugin.getLogger().log(Level.SEVERE, "[GRAPES] Failed to re-send notification: ", e);
            }
        }

        refresh();

        return HttpRedirect.DOT;
    }

    /**
     * Returns all the Grapes resend action of the build (never null, empty list if there is none)
     *
     * @param build AbstractBuild<?, ?>
     * @return List<ResendBuildAction>
     */
    private List<ResendBuildAction> getAllResendActions(final AbstractBuild<?, ?> build) {
        final List<ResendBuildAction> resendActions = new ArrayList<ResendBuildAction>();

        for(Action transientAction: build.getTransientActions()){

            if(transientAction instanceof ResendBuildAction){
                resendActions.add((ResendBuildAction)transientAction);
            }
        }

        return resendActions;
    }

    /**
     * Returns all the Grapes resend Action of a project (null if empty)
     *
     * @param project AbstractProject<?, ?>
     * @return ResendProjectAction
     */
    private ResendProjectAction getAllResendActions(final AbstractProject<?, ?> project) {
        final List<ResendProjectAction> resendProjectActions = project.getActions(ResendProjectAction.class);

        final GrapesConfig config = GrapesPlugin.getGrapesConfiguration(project);
        final Map<AbstractBuild<?,?> , List<ResendBuildAction>> resendBuildActions = new HashMap<AbstractBuild<?, ?>, List<ResendBuildAction>>();
        for(Object run : project.getBuilds()){
            if(run instanceof AbstractBuild){
                final AbstractBuild<?,?> build = (AbstractBuild)run;
                resendBuildActions.put(build, getAllResendActions(build));
            }
        }

        if(!resendBuildActions.isEmpty() &&  config != null){
            return new ResendProjectAction(resendBuildActions, config);
        }

        return null;
    }

}
