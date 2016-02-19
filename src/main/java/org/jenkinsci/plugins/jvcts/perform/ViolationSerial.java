/**
 * Copyright (c) 2016 BSC Praha, spol. s r.o.
 */

package org.jenkinsci.plugins.jvcts.perform;

import java.io.Serializable;

import hudson.plugins.violations.model.Violation;


/**
 *
 * @author stiller
 */
public class ViolationSerial implements Serializable {

    /**
     *
     */
    private static final long serialVersionUID = 1L;
    private int line;
    private String message;
    private String popupMessage;
    private String source;
    private int severityLevel; // 0 is most serious
    private String severity; // the display name for the severity
    private String type;

    public ViolationSerial() {

    }

    public ViolationSerial(Violation v) {
        line = v.getLine();
        message = v.getMessage();
        popupMessage = v.getPopupMessage();
        source = v.getSource();
        severityLevel = v.getSeverityLevel();
        severity = v.getSeverity();
        type = v.getType();
    }

    /**
     * @return the line
     */
    public int getLine() {
        return line;
    }

    /**
     * @param line the line to set
     */
    public void setLine(int line) {
        this.line = line;
    }

    /**
     * @return the message
     */
    public String getMessage() {
        return message;
    }

    /**
     * @param message the message to set
     */
    public void setMessage(String message) {
        this.message = message;
    }

    /**
     * @return the popupMessage
     */
    public String getPopupMessage() {
        return popupMessage;
    }

    /**
     * @param popupMessage the popupMessage to set
     */
    public void setPopupMessage(String popupMessage) {
        this.popupMessage = popupMessage;
    }

    /**
     * @return the source
     */
    public String getSource() {
        return source;
    }

    /**
     * @param source the source to set
     */
    public void setSource(String source) {
        this.source = source;
    }

    /**
     * @return the severityLevel
     */
    public int getSeverityLevel() {
        return severityLevel;
    }

    /**
     * @param severityLevel the severityLevel to set
     */
    public void setSeverityLevel(int severityLevel) {
        this.severityLevel = severityLevel;
    }

    /**
     * @return the severity
     */
    public String getSeverity() {
        return severity;
    }

    /**
     * @param severity the severity to set
     */
    public void setSeverity(String severity) {
        this.severity = severity;
    }

    /**
     * @return the type
     */
    public String getType() {
        return type;
    }

    /**
     * @param type the type to set
     */
    public void setType(String type) {
        this.type = type;
    }

}
