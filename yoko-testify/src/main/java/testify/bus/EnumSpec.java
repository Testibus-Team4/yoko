/*******************************************************************************
* Copyright (c) 2022 IBM Corporation and others.
* All rights reserved. This program and the accompanying materials
* are made available under the terms of the Eclipse Public License v1.0
* which accompanies this distribution, and is available at
* http://www.eclipse.org/legal/epl-v10.html
*
* Contributors:
*     IBM Corporation - Initial implementation
*******************************************************************************/

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

import java.util.stream.Stream;

public interface EnumSpec<E extends Enum<E>> extends TypeSpec<E> {
    @Override
    default String stringify(E e) {
        return e.getDeclaringClass().getName() + "#" + e.name();
    }

    @Override
    default E unstringify(String s) {
        String[] parts = s.split("#");
        Class<E> declaringClass = (Class<E>)findClass(parts[0]);
        String memberName = parts[1];
        return Stream.of(declaringClass.getEnumConstants())
                .filter(mem -> mem.name().equals(memberName))
                .findFirst()
                .orElseThrow(Error::new);
    }

    default Class<?> findClass(String type) {
        try {
            return  Class.forName(type);
        } catch (ClassNotFoundException e) {
            throw (Error)new NoClassDefFoundError(e.getMessage()).initCause(e);
        }
    }
}
