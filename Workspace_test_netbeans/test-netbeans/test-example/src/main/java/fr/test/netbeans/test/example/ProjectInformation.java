package fr.test.netbeans.test.example;

import java.util.Date;

public class ProjectInformation {

	private String _Name = "New project";

	public String getName() {
		return this._Name;
	}

	public void setName(String value) {
		this._Name = value;
	}

	private String _Author = "";

	public String getAuthor() {
		return this._Author;
	}

	public void setAuthor(String value) {
		this._Author = value;
	}

	private Date _Created = new Date();

	public Date getCreated() {
		return this._Created;
	}

	public void setCreated(Date value) {
		this._Created = value;
	}

	private Date _Modified;

	public Date getModified() {
		return this._Modified;
	}

	public void setModified(Date value) {
		this._Modified = value;
	}

	private String _Comment = "";

	public String getComment() {
		return this._Comment;
	}

	public void setComment(String value) {
		this._Comment = value;
	}
}
