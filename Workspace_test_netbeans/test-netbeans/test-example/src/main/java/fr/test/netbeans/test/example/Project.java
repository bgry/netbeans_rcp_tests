package fr.test.netbeans.test.example;

public class Project {

	public Project() {

	}

	private ProjectInformation _Informations = new ProjectInformation();

	public ProjectInformation getInformations() {
		return this._Informations;
	}

	protected void setInformations(ProjectInformation value) {
		this._Informations = value;
	}
}
