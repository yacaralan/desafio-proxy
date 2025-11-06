package org.example.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.core.userdetails.MapReactiveUserDetailsService;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.server.SecurityWebFilterChain;

@Configuration
@EnableWebFluxSecurity
public class SecurityConfig {
	
	@Value("${admin.username}")
	private String adminUser;
	
	@Value("${admin.password}")
	private String adminPass;
	
	@Bean
	public PasswordEncoder passwordEncoder() {
		return new BCryptPasswordEncoder();
	}
	
	@Bean
	public MapReactiveUserDetailsService userDetailsService() {
		PasswordEncoder encoder = passwordEncoder();
		final String storedPassword = encoder.encode(adminPass == null ? "" : adminPass);
		
		UserDetails user = User.withUsername(adminUser)
				.password(storedPassword)
				.roles("ADMIN")
				.build();
		return new MapReactiveUserDetailsService(user);
	}
	
	@Bean
	public SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http) {
		http
				.csrf(ServerHttpSecurity.CsrfSpec::disable)
				.authorizeExchange(ex -> ex
						.pathMatchers("/admin/**").hasRole("ADMIN")
						.anyExchange().permitAll()
				);
		http.httpBasic(Customizer.withDefaults());
		
		return http.build();
	}
}
