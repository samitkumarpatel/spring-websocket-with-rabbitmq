package net.samitkumar.chat_application;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.stereotype.Controller;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

import java.util.Objects;

import static org.springframework.security.config.Customizer.withDefaults;

@SpringBootApplication
public class ChatApplication {

	public static void main(String[] args) {
		SpringApplication.run(ChatApplication.class, args);
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
				.setClientPasscode("guest");
		config.setApplicationDestinationPrefixes("/app");
	}

	@Override
	public void registerStompEndpoints(StompEndpointRegistry registry) {
		registry.addEndpoint("/ws").withSockJS();
	}
}

record ChatMessage(String from, String text, String to) { }

@Controller
@RequiredArgsConstructor
@Slf4j
class ChatController {
	final SimpMessagingTemplate simpMessagingTemplate;

	@MessageMapping("/chat.sendMessage")
	public void sendMessage(ChatMessage message) {
		log.info("Message {} sent from {} to {}", message.text(), message.from(), message.to());
		if(Objects.isNull(message.to())) {
			simpMessagingTemplate.convertAndSend("/topic/public", message);
		} else {
			simpMessagingTemplate.convertAndSendToUser(message.to(), "/queue/private", message);
		}
	}
}

@Configuration
@EnableWebSecurity
class SecurityConfiguration {
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
		UserDetails user = User.withDefaultPasswordEncoder()
				.username("user")
				.password("password")
				.roles("USER")
				.build();
		UserDetails one = User.withDefaultPasswordEncoder()
				.username("one")
				.password("password")
				.roles("USER")
				.build();
		UserDetails two = User.withDefaultPasswordEncoder()
				.username("two")
				.password("password")
				.roles("USER")
				.build();
		return new InMemoryUserDetailsManager(user, one, two);
	}
}