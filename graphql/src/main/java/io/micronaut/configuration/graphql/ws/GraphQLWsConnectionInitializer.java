/*
 * Copyright 2017-2022 original authors
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
package io.micronaut.configuration.graphql.ws;

import io.micronaut.core.annotation.NonNull;
import io.micronaut.websocket.WebSocketSession;

/**
 * An interface for initializing an GraphQLWs connection, e.g. for handling the custom payload.
 *
 * @author Nick Hensel
 * @since 4.0
 */

@FunctionalInterface
public interface GraphQLWsConnectionInitializer {

    /**
     * This method is called, when a GQL_CONNECTION_INIT message is received.
     * @param request The GraphQlWs request
     * @param session The WebSocketSession
     * @since 4.0
     */
    void initialize(@NonNull GraphQLWsInitRequest request, @NonNull WebSocketSession session);
}
