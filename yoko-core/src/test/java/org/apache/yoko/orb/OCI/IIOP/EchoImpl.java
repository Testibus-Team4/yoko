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
package org.apache.yoko.orb.OCI.IIOP;

import testify.bus.Bus;
import testify.bus.StringRef;

class EchoImpl implements Echo {
    private enum BusKey implements StringRef {MESSAGE}
    final Bus bus;

    EchoImpl(Bus bus) {this.bus = bus;}

    @Override
    public String echo(String s) {
        bus.put(BusKey.MESSAGE, s);
        return s;
    }

    static String getLastMessage(Bus bus) { return bus.get(BusKey.MESSAGE); }
}