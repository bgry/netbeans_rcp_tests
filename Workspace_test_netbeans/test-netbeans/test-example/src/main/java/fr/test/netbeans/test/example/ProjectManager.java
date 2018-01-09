package fr.test.netbeans.test.example;

public final class ProjectManager {

	/**
	 * class static
	 */
	private ProjectManager() {
	}

	private static Project _Current = null;

	public static Project getCurrent() {
		return _Current;
	}

	public static void setCurrent(Project value) {
		_Current = value;
	}

	public static void gotoPage(String pageName) {
		System.out.println("[ProjectManager] gotoPage : " + pageName);
	}

	public static String getCurrentPageName() {
		return "xavier";
	}
}
