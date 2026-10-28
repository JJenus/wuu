package com.surf.tracker.notification.api.dto;

public class UpdateTemplateRequest {
    private String name;
    private String templateType;
    private String category;
    private String channel;
    private String subjectTemplate;
    private String bodyTemplate;
    private String language;
    private boolean enabled = true;
    private boolean mandatory = false;
    private String variablesDescription;

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getTemplateType() { return templateType; }
    public void setTemplateType(String templateType) { this.templateType = templateType; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public String getChannel() { return channel; }
    public void setChannel(String channel) { this.channel = channel; }

    public String getSubjectTemplate() { return subjectTemplate; }
    public void setSubjectTemplate(String subjectTemplate) { this.subjectTemplate = subjectTemplate; }

    public String getBodyTemplate() { return bodyTemplate; }
    public void setBodyTemplate(String bodyTemplate) { this.bodyTemplate = bodyTemplate; }

    public String getLanguage() { return language; }
    public void setLanguage(String language) { this.language = language; }

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public boolean isMandatory() { return mandatory; }
    public void setMandatory(boolean mandatory) { this.mandatory = mandatory; }

    public String getVariablesDescription() { return variablesDescription; }
    public void setVariablesDescription(String variablesDescription) { this.variablesDescription = variablesDescription; }
}