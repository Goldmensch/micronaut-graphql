/*
 * Copyright 2017-2020 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package example.graphql;

import example.domain.User;
import example.repository.UserRepository;
import graphql.GraphQLContext;
import graphql.schema.DataFetcher;
import graphql.schema.DataFetchingEnvironment;
import io.micronaut.context.event.ApplicationEventPublisher;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.MutableHttpResponse;
import io.micronaut.http.cookie.Cookie;
import io.micronaut.security.authentication.AuthenticationResponse;
import io.micronaut.security.authentication.Authenticator;
import io.micronaut.security.authentication.UserDetails;
import io.micronaut.security.authentication.UsernamePasswordCredentials;
import io.micronaut.security.event.LoginFailedEvent;
import io.micronaut.security.event.LoginSuccessfulEvent;
import io.micronaut.security.token.jwt.cookie.JwtCookieConfiguration;
import io.micronaut.security.token.jwt.generator.AccessRefreshTokenGenerator;
import io.micronaut.security.token.jwt.generator.JwtGeneratorConfiguration;
import io.micronaut.security.token.jwt.render.AccessRefreshToken;
import io.reactivex.Flowable;

import javax.inject.Singleton;
import java.time.temporal.TemporalAmount;
import java.util.Optional;
import java.util.Random;

/**
 * @author Alexey Zhokhov
 */
@Singleton
public class LoginDataFetcher implements DataFetcher<LoginPayload> {

    private static final int LOGIN_RATE_LIMIT = 10;
    private static int LOGIN_RATE_LIMIT_REMAINING = LOGIN_RATE_LIMIT;

    private final Authenticator authenticator;
    private final ApplicationEventPublisher eventPublisher;
    private final JwtCookieConfiguration jwtCookieConfiguration;
    private final AccessRefreshTokenGenerator accessRefreshTokenGenerator;
    private final JwtGeneratorConfiguration jwtGeneratorConfiguration;

    private final UserRepository userRepository;

    public LoginDataFetcher(Authenticator authenticator,
                            ApplicationEventPublisher eventPublisher,
                            JwtCookieConfiguration jwtCookieConfiguration,
                            AccessRefreshTokenGenerator accessRefreshTokenGenerator,
                            JwtGeneratorConfiguration jwtGeneratorConfiguration, UserRepository userRepository) {
        this.authenticator = authenticator;
        this.eventPublisher = eventPublisher;
        this.jwtCookieConfiguration = jwtCookieConfiguration;
        this.accessRefreshTokenGenerator = accessRefreshTokenGenerator;
        this.jwtGeneratorConfiguration = jwtGeneratorConfiguration;
        this.userRepository = userRepository;
    }

    @Override
    public LoginPayload get(DataFetchingEnvironment environment) throws Exception {
        GraphQLContext graphQLContext = environment.getContext();

        if (LOGIN_RATE_LIMIT_REMAINING <= 0) {
            addRateLimitHeaders(graphQLContext);

            resetRateLimit();

            return LoginPayload.ofError("Rate Limit Exceeded");
        }

        HttpRequest httpRequest = graphQLContext.get("httpRequest");
        MutableHttpResponse<String> httpResponse = graphQLContext.get("httpResponse");

        String username = environment.getArgument("username");
        String password = environment.getArgument("password");

        UsernamePasswordCredentials usernamePasswordCredentials = new UsernamePasswordCredentials(username, password);

        LOGIN_RATE_LIMIT_REMAINING--;

        Flowable<AuthenticationResponse> authenticationResponseFlowable =
                Flowable.fromPublisher(authenticator.authenticate(httpRequest, usernamePasswordCredentials));

        return authenticationResponseFlowable.map(authenticationResponse -> {
            addRateLimitHeaders(graphQLContext);

            if (authenticationResponse.isAuthenticated()) {
                UserDetails userDetails = (UserDetails) authenticationResponse;
                eventPublisher.publishEvent(new LoginSuccessfulEvent(userDetails));

                Optional<Cookie> jwtCookie = accessTokenCookie(userDetails, httpRequest);
                jwtCookie.ifPresent(httpResponse::cookie);

                User user = userRepository.findByUsername(userDetails.getUsername()).orElse(null);

                return LoginPayload.ofUser(user);
            } else {
                eventPublisher.publishEvent(new LoginFailedEvent(authenticationResponse));

                return LoginPayload.ofError(authenticationResponse.getMessage().orElse(null));
            }
        }).blockingFirst();
    }

    private Optional<Cookie> accessTokenCookie(UserDetails userDetails, HttpRequest<?> request) {
        Optional<AccessRefreshToken> accessRefreshTokenOptional = accessRefreshTokenGenerator.generate(userDetails);
        if (accessRefreshTokenOptional.isPresent()) {
            Cookie cookie = Cookie.of(jwtCookieConfiguration.getCookieName(), accessRefreshTokenOptional.get().getAccessToken());
            cookie.configure(jwtCookieConfiguration, request.isSecure());
            Optional<TemporalAmount> cookieMaxAge = jwtCookieConfiguration.getCookieMaxAge();
            if (cookieMaxAge.isPresent()) {
                cookie.maxAge(cookieMaxAge.get());
            } else {
                cookie.maxAge(jwtGeneratorConfiguration.getAccessTokenExpiration());
            }
            return Optional.of(cookie);
        }
        return Optional.empty();
    }

    private void addRateLimitHeaders(GraphQLContext graphQLContext) {
        MutableHttpResponse<String> httpResponse = graphQLContext.get("httpResponse");

        httpResponse.header("X-Login-RateLimit", String.valueOf(LOGIN_RATE_LIMIT));
        httpResponse.header("X-Login-RateLimit-Remaining", String.valueOf(LOGIN_RATE_LIMIT_REMAINING));
    }

    private void resetRateLimit() {
        int random = new Random().nextInt(5);
        if (random == 3) {
            LOGIN_RATE_LIMIT_REMAINING = LOGIN_RATE_LIMIT;
        }
    }

}
