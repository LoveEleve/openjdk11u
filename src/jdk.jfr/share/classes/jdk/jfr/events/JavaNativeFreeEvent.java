/*
 * Copyright (C) 2021 THL A29 Limited, a Tencent company. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 */

package jdk.jfr.events;

import jdk.jfr.Category;
import jdk.jfr.Description;
import jdk.jfr.Label;
import jdk.jfr.DataAmount;
import jdk.jfr.Name;
import jdk.jfr.internal.Type;

@Name(Type.EVENT_NAME_PREFIX + "JavaNativeFree")
@Label("Java Native Free")
@Category("Java Application")
@Description("Free memory when using native")
public final class JavaNativeFreeEvent extends AbstractJDKEvent {

    public static final ThreadLocal<JavaNativeFreeEvent> EVENT =
        new ThreadLocal<>() {
            @Override protected JavaNativeFreeEvent initialValue() {
                return new JavaNativeFreeEvent();
            }
        };

    @Label("addr")
    public long addr;

    public void reset() {
        addr = 0;
    }
}
