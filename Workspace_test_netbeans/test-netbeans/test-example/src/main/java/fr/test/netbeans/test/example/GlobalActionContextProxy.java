package fr.test.netbeans.test.example;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.logging.Logger;
import javax.swing.AbstractAction;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.SwingUtilities;
import org.apache.commons.configuration.Configuration;
import org.netbeans.api.project.FileOwnerQuery;
import org.netbeans.api.project.Project;
import org.netbeans.api.project.ProjectUtils;
import org.netbeans.api.project.ui.OpenProjects;
import org.netbeans.modules.openide.windows.GlobalActionContextImpl;
import org.openide.explorer.ExplorerManager;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;
import org.openide.loaders.DataObject;
import org.openide.nodes.Node;
import org.openide.util.ContextGlobalProvider;
import org.openide.util.Exceptions;
import org.openide.util.Lookup;
import org.openide.util.Lookup.Result;
import org.openide.util.Lookup.Template;
import org.openide.util.LookupEvent;
import org.openide.util.LookupListener;
import org.openide.util.NbBundle.Messages;
import org.openide.util.lookup.AbstractLookup;
import org.openide.util.lookup.InstanceContent;
import org.openide.util.lookup.ProxyLookup;
import org.openide.util.lookup.ServiceProvider;
import org.openide.windows.Mode;
import org.openide.windows.TopComponent;
import org.openide.windows.WindowManager;

/**
 * This class proxies the original ContextGlobalProvider and ensures the current project remains in the GlobalContext regardless of the TopComponent selection.
 * The class also ensures that when a child node is selected within the in Projects tab, the parent Project will be in the lookup.
 *
 * To use this class you must edit the Windows System API module dependency: change the dependency to an implementation version so that the
 * org.netbeans.modules.openide.windows package is on the classpath.
 *
 * @see ContextGlobalProvider
 * @see GlobalActionContextImpl
 * @author Bruce Schubert
 */
@ServiceProvider(service = ContextGlobalProvider.class,
		supersedes = "org.netbeans.modules.openide.windows.GlobalActionContextImpl")
public class GlobalActionContextProxy implements ContextGlobalProvider {

	/**
	 * The native NetBeans global context Lookup provider
	 */
	private final GlobalActionContextImpl globalContextProvider;
	/**
	 * Additional content for our proxy lookup
	 */
	private final InstanceContent content;
	/**
	 * The primary lookup managed by the platform
	 */
	private Lookup globalContextLookup;
	/**
	 * The project lookup managed by resultChanged
	 */
	private Lookup projectLookup;
	/**
	 * The actual proxyLookup returned by this class
	 */
	private Lookup proxyLookup;
	/**
	 * A lookup result that we listen to for Projects
	 */
	private Result<Project> resultProjects;
	/**
	 * Listener for changes resultProjects
	 */
	private final LookupListener resultListener;
	/**
	 * Listener for changes on the TopComponent registry
	 */
	private final PropertyChangeListener registryListener;
	/**
	 * The last project selected
	 */
	private Project lastProject;

	/**
	 * Critical section lock
	 */
	private final Object lock = new Object();
	private static final Logger logger = Logger.getLogger(GlobalActionContextProxy.class.getName());
	public static final String PROJECT_LOGICAL_TAB_ID = "projectTabLogical_tc";
	public static final String PROJECT_FILE_TAB_ID = "projectTab_tc";

	@Messages({
		"LBL_EditorShapePaletteTopComponent=Shapes"
	})
	public GlobalActionContextProxy() {
		this.content = new InstanceContent();
		// The default GlobalContextProvider
		this.globalContextProvider = new GlobalActionContextImpl();
		this.globalContextLookup = this.globalContextProvider.createGlobalContext();

		// Monitor the activation of the Projects Tab TopComponent
		this.registryListener = new RegistryPropertyChangeListener();
		TopComponent.getRegistry().addPropertyChangeListener(this.registryListener);

		// Monitor the existance of a Project in the principle lookup
		this.resultProjects = globalContextLookup.lookupResult(Project.class);
		this.resultListener = new LookupListenerImpl();
		this.resultProjects.addLookupListener(this.resultListener);

		WindowManager.getDefault().invokeWhenUIReady(new Runnable() {
			@Override
			public void run() {
				// Hack to force the current Project selection when the application starts up
				TopComponent tc = WindowManager.getDefault().findTopComponent(PROJECT_LOGICAL_TAB_ID);
				if (tc != null) {
					tc.requestActive();
				}
				displayShapePalette();
			}
		});
	}

	/**
	 * Displays the shape palette.
	 */
	private void displayShapePalette() {
		SwingUtilities.invokeLater(new Runnable() {

			@Override
			public void run() {
				Configuration configuration = CentralLookup.getDefault().lookup(Configuration.class);
				if (configuration.getBoolean("withShapes", false) == true) {
					// Adds to Menu.
					FileObject menuFolder = FileUtil.getConfigFile("Menu/Window");
					try {
						registerAction(EditorShapeTopComponent.class, Bundle.LBL_EditorShapePaletteTopComponent(), null, menuFolder, 333);
					} catch (IOException ex) {
						Exceptions.printStackTrace(ex);
					}

					// Adds to left dock.
					if (!isTopComponentInUserDirCache(EditorShapeTopComponent.class)) {
						EditorShapeTopComponent tcPalette = new EditorShapeTopComponent();
						Mode leftSideMode = WindowManager.getDefault().findMode("leftSlidingSide");
						leftSideMode.dockInto(tcPalette);
						tcPalette.open();
					}
				} else {
					// Removes the file from the cache : avoid it to be reopened by the persistence manager.
					removeTopComponentInUserDirCache(EditorShapeTopComponent.class);
				}
			}
		});

	}

	/**
	 * Removes a component in the userdir cache of Netbeans.
	 *
	 * @param <T> : child class of TopComponent.
	 * @param classType : class type.
	 */
	private <T extends TopComponent> void removeTopComponentInUserDirCache(final Class<T> classType) {
		// Gets the directory which contains cache on Components.
		Path compDirPath = getUserDirComponentsSettingPath();

		// It the directory exists, removes the files referencing the component.
		File compDirFile = compDirPath.toFile();
		if (compDirFile.exists()) {
			// Gets the files referencing the component.
			File[] compFiles = compDirFile.listFiles(new FilenameFilter() {
				@Override
				public boolean accept(File dir, String name) {
					StringBuilder filenameMatcher = new StringBuilder("^");
					filenameMatcher.append(classType.getSimpleName());
					filenameMatcher.append("(_\\d*)?");
					filenameMatcher.append(".settings$");
					return name != null && name.matches(filenameMatcher.toString());
				}
			});

			// Removes the files referencing the component.
			if (compFiles != null) {
				for (int i = compFiles.length - 1; i >= 0; i--) {
					File compFile = compFiles[i];
					compFile.delete();
				}
			}
		}
	}

	/**
	 * Checks if a component appears in the userdir cache of Netbeans.
	 *
	 * @param <T> : child class of TopComponent.
	 * @param classType : class type.
	 * @return true if the component is in the cache, false otherwise.
	 */
	private <T extends TopComponent> boolean isTopComponentInUserDirCache(final Class<T> classType) {
		boolean isInCache = false;

		// Gets the directory which contains cache on Components.
		Path compDirPath = getUserDirComponentsSettingPath();

		// It the directory exists, checks if the component has already been opened.
		File compDirFile = compDirPath.toFile();
		if (compDirFile.exists()) {
			// Gets the files referencing the component.
			File[] compFiles = compDirFile.listFiles(new FilenameFilter() {
				@Override
				public boolean accept(File dir, String name) {
					StringBuilder filenameMatcher = new StringBuilder("^");
					filenameMatcher.append(classType.getSimpleName());
					filenameMatcher.append("(_\\d*)?");
					filenameMatcher.append(".settings$");
					return name != null && name.matches(filenameMatcher.toString());
				}
			});

			// If there is at least 1 file, the component has already been opened.
			if (compFiles != null && compFiles.length > 0) {
				isInCache = true;
			}
		}
		return isInCache;
	}

	/**
	 * Gets the path of the component directory in the userdir. The directory contains the settings files for the TopComponent of Netbeans. This is usefull to
	 * get the state when the application stopped.
	 *
	 * @return path of the component directory in the userdir.
	 */
	private Path getUserDirComponentsSettingPath() {
		String userDir = System.getProperty("netbeans.user");
		Path compDirPath = Paths.get(userDir).resolve("config/Windows2Local/Components/");
		return compDirPath;
	}

	/**
	 * Registers menu action.
	 *
	 * @param classType : class type.
	 * @param menuDisplayName : menu name.
	 * @param icon : icon.
	 * @param menuFolder : menu folder.
	 * @throws IOException : exception when adding the menu item.
	 */
	private void registerAction(Class<? extends TopComponent> classType, String menuDisplayName, Icon icon, FileObject menuFolder, int position) throws IOException {

		// Create if missing. 
		FileObject menu = menuFolder.getFileObject(classType.getSimpleName(), "instance");

		// With this approach I noticed that I only need to create the instance the first time. 
		if (menu == null) {
			menu = menuFolder.createData(classType.getSimpleName(), "instance");
			AbstractAction action = new MenuAction(classType, menuDisplayName, icon);
			menu.setAttribute("instanceCreate", action);
			menu.setAttribute("instanceClass", action.getClass().getName());
			menu.setAttribute("position", position);
		}
	}

	/**
	 * Returns a ProxyLookup that adds the current Project instance to the global selection returned by Utilities.actionsGlobalContext().
	 *
	 * @return a ProxyLookup that includes the original global context lookup.
	 */
	@Override
	public Lookup createGlobalContext() {
		if (proxyLookup == null) {
			logger.config("Creating a proxy for Utilities.actionsGlobalContext()");
			// Create the two lookups that will make up the proxy
			projectLookup = new AbstractLookup(content);
			proxyLookup = new ProxyLookup(globalContextLookup, projectLookup);
		}
		return proxyLookup;
	}

	/**
	 * This class populates the proxy lookup with the currently selected project found in the Projects tab.
	 */
	private class RegistryPropertyChangeListener implements PropertyChangeListener {

		private TopComponent projectsTab = null;

		@Override
		public void propertyChange(PropertyChangeEvent event) {
			if (event.getPropertyName().equals(TopComponent.Registry.PROP_ACTIVATED_NODES)
					|| event.getPropertyName().equals(TopComponent.Registry.PROP_ACTIVATED)) {
				// Get a reference to the Projects window
				if (projectsTab == null) {
					projectsTab = WindowManager.getDefault().findTopComponent(PROJECT_LOGICAL_TAB_ID);
					if (projectsTab == null) {
						logger.severe("propertyChange: cannot find the Projects logical window ("
								+ PROJECT_LOGICAL_TAB_ID + ")");
						return;
					}
				}
				// Look for the current project in the Projects window when activated and handle 
				// special case at startup when lastProject hasn't been initialized.            
				Node[] nodes = null;
				TopComponent activated = TopComponent.getRegistry().getActivated();
				if (activated != null && activated.equals(projectsTab)) {
					logger.finer("propertyChange: processing activated nodes");
					nodes = projectsTab.getActivatedNodes();
				} else if (lastProject == null) {
					logger.finer("propertyChange: processing selected nodes");
					ExplorerManager em = ((ExplorerManager.Provider) projectsTab).getExplorerManager();
					nodes = em.getSelectedNodes();
				}
				// Find and use the first project that owns a node
				if (nodes != null) {
					for (Node node : nodes) {
						Project project = findProjectThatOwnsNode(node);
						if (project != null) {
							synchronized (lock) {
								// Remember this project for when the Project Tab goes out of focus
								lastProject = project;

								// Add this project to the proxy if it's not in the global lookup
								if (!resultProjects.allInstances().contains(lastProject)) {
									logger.finer("propertyChange: Found project ["
											+ ProjectUtils.getInformation(lastProject).getDisplayName()
											+ "] that owns current node.");
									updateProjectLookup(lastProject);
								}
							}
							break;
						}
					}
				}
			}
		}
	}

	/**
	 * This class listens for changes in the Project results, and ensures a Project remains in the Utilities.actionsGlobalContext() if a project is open.
	 */
	private class LookupListenerImpl implements LookupListener {

		@Override
		public void resultChanged(LookupEvent event) {
			logger.finer("resultChanged: Entered...");
			synchronized (lock) {
				// First, handle projects in the principle lookup
				if (resultProjects.allInstances().size() > 0) {
					// Clear the proxy, and remember this project. 
					// Note: not handling multiple selection of projects.
					clearProjectLookup();
					lastProject = resultProjects.allInstances().iterator().next();

					logger.finer("resultChanged: Found project ["
							+ ProjectUtils.getInformation(lastProject).getDisplayName()
							+ "] in the normal lookup.");
				} else if (OpenProjects.getDefault().getOpenProjects().length == 0) {
					clearProjectLookup();
					lastProject = null;
				} else {
					if (lastProject == null) {
						// Find the project that owns the current Node
						Node currrentNode = globalContextLookup.lookup(Node.class);
						Project project = findProjectThatOwnsNode(currrentNode);
						if (project != null) {
							lastProject = project;
							logger.finer("resultChanged: Found project ["
									+ ProjectUtils.getInformation(lastProject).getDisplayName()
									+ "] that owns current node.");
						}
					}
					// Add the last used project to our internal lookup
					if (lastProject != null) {
						updateProjectLookup(lastProject);
					}
				}
			}
		}
	}

	/**
	 * Unconditionally clears the project lookup.
	 */
	private void clearProjectLookup() {
		Collection<? extends Project> projects = projectLookup.lookupAll(Project.class);
		for (Project project : projects) {
			content.remove(project);
		}
	}

	/**
	 * Replaces the project lookup content.
	 *
	 * @param project to place in the project lookup.
	 */
	private void updateProjectLookup(Project project) {
		if (project == null) {
			throw new IllegalArgumentException("project cannot be null.");
		}
		// Add the project if an instance of it is not already in the lookup
		Template<Project> template = new Template<Project>(Project.class, null, project);
		if (projectLookup.lookupItem(template) == null) {
			clearProjectLookup();
			content.add(project);
			logger.fine("updateProjectLookup: added ["
					+ ProjectUtils.getInformation(lastProject).getDisplayName()
					+ "] to the proxy lookup.");
		}
	}

	/**
	 * Recursively searches the node hierarchy for the project that owns a node.
	 *
	 * @param node a node to test for a Project in its or its ancestor's lookup.
	 * @return the Project that owns the node, or null if not found
	 */
	private static Project findProjectThatOwnsNode(Node node) {
		if (node != null) {
			Project project = node.getLookup().lookup(Project.class);
			if (project == null) {
				DataObject dataObject = node.getLookup().lookup(DataObject.class);
				if (dataObject != null) {
					project = FileOwnerQuery.getOwner(dataObject.getPrimaryFile());
				}
			}
			return (project == null) ? findProjectThatOwnsNode(node.getParentNode()) : project;
		} else {
			return null;
		}
	}
}
