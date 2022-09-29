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

import java.util.function.Consumer;

// Although some of the methods here are fluent in design
// (i.e. they return a Bus object suitable for method chaining)
// the internal implementations are expected to return null.
// The fluency is an affordance purely for the code calling
// objects accessible outside the package.
@SuppressWarnings("UnusedReturnValue")
interface EventBus {
    <K extends Enum<K> & TypeSpec<?>> boolean hasKey(K key);
    <K extends Enum<K> & TypeSpec<T>, T> T get(K key);
    <K extends Enum<K> & TypeSpec<T>, T> T peek(K key);
    <K extends Enum<K> & TypeSpec<? super T>, T> Bus put(K key, T value);
    <K extends Enum<K> & TypeSpec<T>, T> Bus put(K key);
    <K extends Enum<K> & TypeSpec<T>, T> Bus onMsg(K key, Consumer<T> action);
    <K extends Enum<K> & TypeSpec<K>> Bus onMsg(K key, Runnable action);
}
