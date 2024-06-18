package net.samitkumar.chat_application;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.messaging.handler.annotation.Headers;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.security.Principal;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@SpringBootApplication
public class ChatApplication {

	public static void main(String[] args) {
		SpringApplication.run(ChatApplication.class, args);
	}

	@Bean
	List<String> allUsers() {
		return List.of("user", "one", "two");
	}
}

@Configuration
@EnableWebSocketMessageBroker
class WebsocketConfig implements WebSocketMessageBrokerConfigurer  {
	@Override
	public void configureMessageBroker(MessageBrokerRegistry config) {
		config.enableStompBrokerRelay("/queue", "/topic")
				.setRelayHost("localhost")
				.setRelayPort(61613)
				.setClientLogin("guest")
				.setClientPasscode("guest")
				.setUserDestinationBroadcast("/topic/unresolved-user")
				.setUserRegistryBroadcast("/topic/registry");
		config.setApplicationDestinationPrefixes("/app");
	}

	@Override
	public void registerStompEndpoints(StompEndpointRegistry registry) {
		registry.addEndpoint("/ws").setAllowedOriginPatterns("*").withSockJS();
	}
}

record ChatMessage(String from, String text, String to) { }

@Controller
@RequiredArgsConstructor
@Slf4j
class ChatController {
	final SimpMessagingTemplate simpMessagingTemplate;
	final List<String> allUsers;
	final InMemoryUserDetailsManager inMemoryUserDetailsManager;

	@GetMapping("/all/user")
	@ResponseBody
	public Iterable<UserDetails> users() {
		return allUsers.stream().map(inMemoryUserDetailsManager::loadUserByUsername).collect(Collectors.toSet());
	}

	@GetMapping("/me")
	@ResponseBody
	public Principal me(Principal principal) {
		return principal;
	}

	@MessageMapping("/chat.sendMessage")
	public void sendMessage(@Payload ChatMessage message, @Headers Map<Object, Object> headersMap, @Headers SimpMessageHeaderAccessor simpMessageHeaderAccessor, Principal principal) {

		//manipulate the headers , so the session id is the username
		//simpMessageHeaderAccessor.setSessionId(principal.getName());

		log.info("Headers: {}", headersMap);
		log.info("SimpMessageHeaderAccessor: {}", simpMessageHeaderAccessor);
		log.info("Principal: {}", principal);
		log.info("Message: {}", message);

		if(Objects.isNull(message.to())) {
			simpMessagingTemplate.convertAndSend("/topic/public", message);
		} else {
			simpMessagingTemplate.convertAndSendToUser(message.to(), "/queue/private", message); //The queue name would be /user/{username}/queue/private
		}
	}
}

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
class SecurityConfiguration {
	final List<String> allUsers;

	@Bean
	public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
		http
				.authorizeHttpRequests((authorize) -> authorize
						.requestMatchers("/login").permitAll()
						.anyRequest().authenticated()
				)
				.httpBasic(Customizer.withDefaults())
				.formLogin(Customizer.withDefaults());

		return http.build();
	}

	@Bean
	public InMemoryUserDetailsManager userDetailsService() {
		return allUsers
				.stream()
				.map(username -> User.withDefaultPasswordEncoder().username(username).password("password").roles("USER").build())
				.collect(Collectors.collectingAndThen(Collectors.toList(), InMemoryUserDetailsManager::new));
	}
}