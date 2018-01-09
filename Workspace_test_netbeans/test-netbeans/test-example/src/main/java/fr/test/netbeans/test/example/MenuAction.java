package fr.test.netbeans.test.example;

import java.awt.event.ActionEvent;
import java.lang.reflect.InvocationTargetException;
import java.util.Set;
import javax.swing.AbstractAction;
import static javax.swing.Action.NAME;
import javax.swing.Icon;
import javax.swing.JMenuItem;
import javax.swing.SwingUtilities;
import org.openide.util.Exceptions;
import org.openide.util.actions.Presenter;
import org.openide.windows.Mode;
import org.openide.windows.TopComponent;
import org.openide.windows.WindowManager;

/**
 *
 * @author BGRY
 */
public final class MenuAction extends AbstractAction implements Presenter.Menu {

	private final Class<? extends TopComponent> tcClass;
	private JMenuItem menu;

	public MenuAction(Class<? extends TopComponent> tcClass, String displayName, Icon icon) {
		putValue(NAME, displayName);
		putValue(SMALL_ICON, icon);
		this.tcClass = tcClass;
		menu = new JMenuItem(this);
	}

	@Override
	public void actionPerformed(ActionEvent ae) {
		SwingUtilities.invokeLater(new Runnable() {

			@Override
			public void run() {
				try {
					// Checks that the menu is not already open.
					Set<TopComponent> openComponents = WindowManager.getDefault().getRegistry().getOpened();
					for (TopComponent openComponent : openComponents) {
						if (openComponent.getClass().equals(tcClass)) {
							openComponent.requestActive();
							return;
						}
					}

					// Opens the menu.
					//WindowManager.getDefault().findTopComponent(tcClass.)
					TopComponent topComponent = WindowManager.getDefault().findTopComponent(getIdForTopComponentClass(tcClass));
					if (topComponent == null) {
						topComponent = tcClass.getConstructor().newInstance();
						if (!topComponent.isOpened()) {
							Mode leftSideMode = WindowManager.getDefault().findMode("leftSlidingSide");
							leftSideMode.dockInto(topComponent);
						}
					}
					topComponent.open();
					topComponent.requestActive();
				} catch (NoSuchMethodException | SecurityException | InstantiationException | IllegalAccessException | IllegalArgumentException | InvocationTargetException ex) {
					Exceptions.printStackTrace(ex);
				}
			}
		});

	}

	/**
	 * Reads the ID in annotation of the component class.
	 *
	 * @param classType : class type of the component.
	 * @return ID for this type of component.
	 */
	private String getIdForTopComponentClass(Class<? extends TopComponent> classType) {
		String preferredId = null;
		TopComponent.Description idTopComponent = classType.getAnnotation(TopComponent.Description.class);
		if (idTopComponent != null) {
			preferredId = idTopComponent.preferredID();
		}
		return preferredId;
	}

	@Override

	public JMenuItem getMenuPresenter() {
		return menu;
	}
}
