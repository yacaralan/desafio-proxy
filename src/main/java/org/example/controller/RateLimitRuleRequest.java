package org.example.controller;

public class RateLimitRuleRequest {
	private String type;
	private String ip;
	private String path;
	private Long rpm;
	
	public RateLimitRuleRequest() {
	}
	
	public String getType() {
		return type;
	}
	
	public void setType(String type) {
		this.type = type;
	}
	
	public String getIp() {
		return ip;
	}
	
	public void setIp(String ip) {
		this.ip = ip;
	}
	
	public String getPath() {
		return path;
	}
	
	public void setPath(String path) {
		this.path = path;
	}
	
	public Long getRpm() {
		return rpm;
	}
	
	public void setRpm(Long rpm) {
		this.rpm = rpm;
	}
}

