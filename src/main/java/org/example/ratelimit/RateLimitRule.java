package org.example.ratelimit;

import java.util.Objects;
import java.util.UUID;

public final class RateLimitRule {
	
	private final String id;
	private final RateLimitType type;
	private final String ip;
	private final String path;
	private final long rpm;
	
	public RateLimitRule(RateLimitType type, String ip, String path, long rpm) {
		this(UUID.randomUUID().toString(), type, ip, path, rpm);
	}
	
	public RateLimitRule(String id, RateLimitType type, String ip, String path, long rpm) {
		this.id = Objects.requireNonNull(id, "id");
		this.type = Objects.requireNonNull(type, "type");
		this.ip = ip;
		this.path = path;
		this.rpm = rpm;
	}
	
	public boolean matches(String ip, String path) {
		return type.matches(this, ip, path);
	}
	
	public String computeKey(String ip, String path) {
		return type.computeKey(this, ip, path);
	}
	
	public String getId() {
		return id;
	}
	
	public RateLimitType getType() {
		return type;
	}
	
	public String getIp() {
		return ip;
	}
	
	public String getPath() {
		return path;
	}
	
	public long getRpm() {
		return rpm;
	}
	
	@Override
	public String toString() {
		return "RateLimitRule{" + "id='" + id + '\'' + ", type=" + type + ", ip='" + ip + '\'' + ", path='" + path + '\'' + ", rpm=" + rpm + '}';
	}
	
	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (o == null || getClass() != o.getClass()) {
			return false;
		}
		RateLimitRule that = (RateLimitRule) o;
		return id.equals(that.id);
	}
	
	@Override
	public int hashCode() {
		return id.hashCode();
	}
}