package net.samitkumar.chat_application;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.handler.annotation.Headers;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.messaging.MessageSecurityMetadataSourceRegistry;
import org.springframework.security.config.annotation.web.socket.AbstractSecurityWebSocketMessageBrokerConfigurer;
import org.springframework.security.config.annotation.web.socket.EnableWebSocketSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;
import org.springframework.web.socket.messaging.SessionConnectEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;
import org.springframework.web.socket.messaging.SessionSubscribeEvent;
import org.springframework.web.socket.messaging.SessionUnsubscribeEvent;

import java.security.Principal;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@SpringBootApplication
@Slf4j
public class ChatApplication {

	public static void main(String[] args) {
		SpringApplication.run(ChatApplication.class, args);
	}

	@Bean
	List<String> allUsers() {
		return List.of("user", "one", "two");
	}

	@EventListener
	public void sessionConnectEvent(SessionConnectEvent sessionConnectEvent) {
		log.info("SessionConnectEvent: {}", sessionConnectEvent);
	}

	@EventListener
	public void sessionDisconnectEvent(SessionDisconnectEvent sessionDisconnectEvent) {
		log.info("SessionDisconnectEvent: {}", sessionDisconnectEvent);
	}

	@EventListener
	void sessionSubscribeEvent(SessionSubscribeEvent sessionSubscribeEvent) {
		log.info("SessionSubscribeEvent: {}", sessionSubscribeEvent);
	}

	@EventListener
	void sessionUnsubscribeEvent(SessionUnsubscribeEvent sessionUnsubscribeEvent) {
		log.info("SessionUnsubscribeEvent: {}", sessionUnsubscribeEvent);
	}

}

@Configuration
@EnableWebSocketMessageBroker
@Slf4j
class WebsocketConfig implements WebSocketMessageBrokerConfigurer  {

	@Override
	public void configureMessageBroker(MessageBrokerRegistry config) {
		config.enableStompBrokerRelay("/queue", "/topic")
				.setRelayHost("localhost")
				.setRelayPort(61613)
				.setClientLogin("guest")
				.setClientPasscode("guest");
		config.setApplicationDestinationPrefixes("/app");
		config.setPreservePublishOrder(true);
	}

	@Override
	public void registerStompEndpoints(StompEndpointRegistry registry) {
		registry.addEndpoint("/ws")
				.setAllowedOriginPatterns("*")
				.withSockJS()
				.setHeartbeatTime(60_000);
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
		log.info("SessionId: {}", simpMessageHeaderAccessor.getSessionId());
		log.info("SessionAttributes.sessionId: {}", simpMessageHeaderAccessor.getSessionAttributes().get("sessionId"));
		log.info("Principal: {}", principal);
		log.info("Message: {}", message);

		if(Objects.isNull(message.to())) {
			simpMessagingTemplate.convertAndSend("/topic/public", message, Map.of("auto-delete","true"));
		} else {
			simpMessagingTemplate.convertAndSendToUser(message.to(), "/queue/private", message);
			//or you can use /user/{username}/queue/private queue to send message to specific user
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