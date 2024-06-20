package net.samitkumar.chat_application;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.event.ApplicationStartedEvent;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.MappedCollection;
import org.springframework.data.relational.core.mapping.Table;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.messaging.handler.annotation.Headers;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.stereotype.Controller;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;
import org.springframework.web.socket.messaging.SessionConnectEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;
import org.springframework.web.socket.messaging.SessionSubscribeEvent;
import org.springframework.web.socket.messaging.SessionUnsubscribeEvent;

import java.security.Principal;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;


@SpringBootApplication
@Slf4j
@RequiredArgsConstructor
public class ChatApplication {
	final UserRepository userRepository;
	final PasswordEncoder passwordEncoder;
	public static void main(String[] args) {
		SpringApplication.run(ChatApplication.class, args);
	}

	@EventListener
	void onApplicationEvent(ApplicationStartedEvent event) {
		//delete all users
		userRepository.deleteAll();

		//create some users
		userRepository
				.saveAll(
						List.of(
							new Users(null, "one", passwordEncoder.encode("password"), Set.of()),
							new Users(null, "two", passwordEncoder.encode("password"), Set.of()),
							new Users(null, "three", passwordEncoder.encode("password"), Set.of())
						)
				)
				.forEach(user -> log.info("User: {}", user));
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


//Websocket , STOMP and SocketJS configuration
@Configuration
@EnableWebSocketMessageBroker
@Slf4j
class WebsocketConfig implements WebSocketMessageBrokerConfigurer  {

	@Value("${spring.rabbitmq.relay.host}")
	private String relayHost;

	@Value("${spring.rabbitmq.relay.port}")
	private int relayPort;

	@Override
	public void configureMessageBroker(MessageBrokerRegistry config) {
		config.enableStompBrokerRelay("/queue", "/topic")
				.setRelayHost(relayHost)
				.setRelayPort(relayPort)
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
class MessageController {
	final SimpMessagingTemplate simpMessagingTemplate;

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
		Map<String, Object> headers = Map.of("auto-delete", true, "x-message-ttl", 6000, "id", principal.getName());
		if(Objects.isNull(message.to())) {
			simpMessagingTemplate.convertAndSend("/topic/public", message);
		} else {
			simpMessagingTemplate.convertAndSendToUser(message.to(), "/queue/private", message, headers);
			//or you can use /user/{username}/queue/private queue to send message to specific user
		}
	}
}


//api
@RestController
@RequiredArgsConstructor
@CrossOrigin(originPatterns = "*")
class ChatRestController {
	final UserRepository userRepository;

	@GetMapping("/all/user")
	@ResponseBody
	public Iterable<Users> users() {
		return userRepository
				.findAll()
				.stream()
				.map(user -> new Users(user.id(), user.username(), null, null))
				.toList();
	}

	@GetMapping("/me")
	@ResponseBody
	public Principal me(Principal principal) {
		return principal;
	}
}


//security
@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
class SecurityConfiguration {
	final List<String> allUsers;

	@Bean
	public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
		http
				.cors(httpSecurityCorsConfigurer -> httpSecurityCorsConfigurer.configurationSource(request -> {
					var cors = new org.springframework.web.cors.CorsConfiguration();
					cors.setAllowedOriginPatterns(List.of("*"));
					return cors;
				}))
				.csrf(AbstractHttpConfigurer::disable)
				.authorizeHttpRequests((authorize) -> authorize
						.requestMatchers("/login").permitAll()
						.anyRequest().authenticated()
				)
				.httpBasic(Customizer.withDefaults())
				.formLogin(Customizer.withDefaults());

		return http.build();
	}

	@Bean
	PasswordEncoder passwordEncoder() {
		return new BCryptPasswordEncoder();
	}
}

@Service
@RequiredArgsConstructor
class UserService implements UserDetailsService {
	final UserRepository userRepository;

	@Override
	public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
		return userRepository.findByUsername(username);
	}
}

//database
@Table("users")
record Users(@Id Long id, String username, String password, @MappedCollection(idColumn = "id") Set<Groups> groups) implements UserDetails {
	@Override
	public Collection<? extends GrantedAuthority> getAuthorities() {
		return List.of();
	}

	@Override
	public String getPassword() {
		return password;
	}

	@Override
	public String getUsername() {
		return username;
	}

	@Override
	public boolean isAccountNonExpired() {
		return true;
	}

	@Override
	public boolean isAccountNonLocked() {
		return true;
	}

	@Override
	public boolean isCredentialsNonExpired() {
		return true;
	}

	@Override
	public boolean isEnabled() {
		return true;
	}
}

interface UserRepository extends ListCrudRepository<Users, Long>{
	Users findByUsername(String username);
}

@Table("groups")
record Groups(@Id Long id, String name) {}
interface GroupRepository extends ListCrudRepository<Groups, Long> {}

@Table("group_memberships")
record GroupMemberships(Long userId, Long groupId, LocalDateTime joinedAt) {}
interface GroupMembershipRepository extends ListCrudRepository<GroupMemberships, Long> {}

@Table("messages")
record Messages(@Id Long id, Long senderId, Long receiverId, String content, LocalDateTime sentAt) {}
interface MessageRepository extends ListCrudRepository<Messages, Long> {}

@Table("chat_history")
record ChatHistory(@Id Long id, Long messageId, Long groupId, LocalDateTime createdAt) {}
interface ChatHistoryRepository extends ListCrudRepository<ChatHistory, Long> {}