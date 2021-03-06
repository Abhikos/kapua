/*******************************************************************************
 * Copyright (c) 2011, 2016 Eurotech and/or its affiliates and others
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *      Eurotech - initial API and implementation
 *******************************************************************************/
package org.eclipse.kapua.service.device.call.kura.model.configuration;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;

/**
 * A container for XmlConfigPropertyAdapted organized into an array.
 */
@XmlAccessorType(XmlAccessType.FIELD)
public class KuraXmlConfigPropertiesAdapted
{
    @XmlElement(name="property")
    private XmlConfigPropertyAdapted[] properties;

    public KuraXmlConfigPropertiesAdapted() {
    }

    public XmlConfigPropertyAdapted[] getProperties() {
        return properties;
    }

    public void setProperties(XmlConfigPropertyAdapted[] properties) {
        this.properties = properties;
    }
}
