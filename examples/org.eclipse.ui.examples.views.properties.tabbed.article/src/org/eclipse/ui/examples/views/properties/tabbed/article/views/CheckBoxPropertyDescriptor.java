/*******************************************************************************
 * Copyright (c) 2006 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.ui.examples.views.properties.tabbed.article.views;

import org.eclipse.jface.viewers.CellEditor;
import org.eclipse.jface.viewers.CheckboxCellEditor;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.ui.views.properties.PropertyDescriptor;

public class CheckBoxPropertyDescriptor
    extends PropertyDescriptor {

    public CheckBoxPropertyDescriptor(Object id, String displayName) {
        super(id, displayName);
    }

    public CellEditor createPropertyEditor(Composite parent) {
        CellEditor editor = new CheckboxCellEditor(parent);
        if (getValidator() != null)
            editor.setValidator(getValidator());
        return editor;
    }
}
