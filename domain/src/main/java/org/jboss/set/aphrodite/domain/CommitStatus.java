/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2016, Red Hat, Inc., and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */

package org.jboss.set.aphrodite.domain;

public enum CommitStatus {

    ERROR("error"), FAILURE("failure"), PENDING("pending"), SUCCESS("success"), UNKNOWN("unknown");

    private final String status;

    CommitStatus(final String status) {
        this.status = status;
    }
    public static CommitStatus fromString(String status) {
        if (status != null) {
            for (CommitStatus b : CommitStatus.values()) {
                if (status.equalsIgnoreCase(b.status)) {
                    return b;
                }
            }
        }
        return null;
    }
    @Override
    public String toString() {
        return status;
    }
}
