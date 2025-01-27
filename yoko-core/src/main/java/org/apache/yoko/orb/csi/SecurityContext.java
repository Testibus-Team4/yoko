/**
 *
 * Licensed to the Apache Software Foundation (ASF) under one or more
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
package org.apache.yoko.orb.csi;

import org.apache.yoko.osgi.ProviderLocator;

import javax.security.auth.Subject;
import javax.security.auth.login.LoginException;

import static java.security.AccessController.doPrivileged;
import static org.apache.yoko.util.PrivilegedActions.GET_CONTEXT_CLASS_LOADER;
import static org.apache.yoko.util.PrivilegedActions.getNoArgConstructor;
import static org.apache.yoko.util.PrivilegedActions.getSysProp;

public abstract class SecurityContext {

    private static SecurityContextDelegate delegate;

    public static void setAuthenticatedSubject(Subject subject) {
        getDelegate().setAuthenticatedSubject(subject);
    }

    private static SecurityContextDelegate getDelegate() {

        if (delegate == null) {
            delegate = allocateDelegate();
        }

        return delegate;
    }

    private static SecurityContextDelegate allocateDelegate() {
        String className = doPrivileged(getSysProp("org.freeorb.csi.SecurityContextClass", "org.freeorb.csi.DefaultSecurityContextDelegate"));

        try {
            Class<? extends SecurityContextDelegate> c = ProviderLocator.loadClass(className, SecurityContext.class, doPrivileged(GET_CONTEXT_CLASS_LOADER));
            return doPrivileged(getNoArgConstructor(c)).newInstance();
        } catch (Exception e) {
            throw new InternalError("unable to attach to SecurityContext", e);
        }
    }

    public static Subject anonymousLogin() throws LoginException {
        return getDelegate().anonymousLogin();
    }

    public static Subject login(String name, String realm, String password) throws LoginException {
        return getDelegate().login(name, realm, password);
    }

    public static Subject delegate(String user, String domain) {
        return getDelegate().delegate(user, domain);
    }

    public static AuthenticationInfo getAuthenticationInfo() {
        return getDelegate().getAuthenticationInfo();
    }

}
