/*
 *  Licensed to the Apache Software Foundation (ASF) under one or more
 *  contributor license agreements.  See the NOTICE file distributed with
 *  this work for additional information regarding copyright ownership.
 *  The ASF licenses this file to You under the Apache License, Version 2.0
 *  (the "License"); you may not use this file except in compliance with
 *  the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package testify.bus;

import static java.util.Objects.requireNonNull;

interface QualifiedBus extends BusWrapper {
    String GLOBAL_USER = "global";
    String DELIMITER = "::";
    String user();

    @Override
    default String transform(String key) {  return user() + DELIMITER + validate(key); }

    @Override
    default String untransform(String key) {
        final String prefix = user() + DELIMITER;
        return key.startsWith(prefix) ? key.substring(prefix.length()) : null;
    };

    @Override
    default String isLoggingEnabled(LogLevel level) { return isLoggingEnabled(user(), level); }

    @Override
    default void enableLogging(LogLevel level, String pattern) { enableLogging(user(), level, pattern); }

    default boolean isGlobal() { return GLOBAL_USER.equals(user()); }

    @Override
    default void put(String key, String value) {
        BusWrapper.super.put(key, value);
        if (isGlobal()) return;
        bus().global().put(key, value);
    }

    static String validate(String name) {
        if (requireNonNull(name).contains(DELIMITER))
            throw new Error("Names may not contain '" + DELIMITER + "' (name was '" + name + "')");
        return name;
    }
}
