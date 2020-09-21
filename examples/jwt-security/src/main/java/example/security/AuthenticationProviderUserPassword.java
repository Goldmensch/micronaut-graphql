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
package example.security;

import edu.umd.cs.findbugs.annotations.Nullable;
import example.domain.User;
import example.repository.UserRepository;
import io.micronaut.http.HttpRequest;
import io.micronaut.security.authentication.AuthenticationFailed;
import io.micronaut.security.authentication.AuthenticationFailureReason;
import io.micronaut.security.authentication.AuthenticationProvider;
import io.micronaut.security.authentication.AuthenticationRequest;
import io.micronaut.security.authentication.AuthenticationResponse;
import io.micronaut.security.authentication.UserDetails;
import io.reactivex.Flowable;
import org.reactivestreams.Publisher;

import javax.inject.Singleton;
import java.util.Optional;

/**
 * @author Alexey Zhokhov
 */
@Singleton
public class AuthenticationProviderUserPassword implements AuthenticationProvider {

    private final UserRepository userRepository;

    public AuthenticationProviderUserPassword(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public Publisher<AuthenticationResponse> authenticate(@Nullable HttpRequest<?> httpRequest,
                                                          AuthenticationRequest<?, ?> authenticationRequest) {
        Optional<User> user = userRepository.findByUsername((String) authenticationRequest.getIdentity());

        if (!user.isPresent()) {
            return Flowable.just(new AuthenticationFailed(AuthenticationFailureReason.USER_NOT_FOUND));
        }

        if (authenticationRequest.getSecret().equals(user.get().getPassword())) {
            return Flowable.just(new UserDetails(user.get().getUsername(), user.get().getRoles()));
        }

        return Flowable.just(new AuthenticationFailed(AuthenticationFailureReason.CREDENTIALS_DO_NOT_MATCH));
    }

}
