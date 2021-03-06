/*******************************************************************************
 * Copyright (c) 2011, 2016 Eurotech and/or its affiliates and others
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Eurotech - initial API and implementation
 *
 *******************************************************************************/
package org.eclipse.kapua.broker.core.message;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import javax.jms.BytesMessage;
import javax.jms.JMSException;
import javax.jms.Topic;

import org.apache.activemq.command.ActiveMQMessage;
import org.eclipse.kapua.KapuaException;
import org.eclipse.kapua.broker.core.converter.KapuaConverter;
import org.eclipse.kapua.broker.core.plugin.AclConstants;
import org.eclipse.kapua.broker.core.plugin.ConnectorDescriptor;
import org.eclipse.kapua.broker.core.plugin.ConnectorDescriptor.MESSAGE_TYPE;
import org.eclipse.kapua.message.KapuaMessage;
import org.eclipse.kapua.model.id.KapuaId;
import org.eclipse.kapua.service.device.call.message.DeviceMessage;
import org.eclipse.kapua.translator.Translator;
import org.eclipse.kapua.transport.message.jms.JmsMessage;
import org.eclipse.kapua.transport.message.jms.JmsPayload;
import org.eclipse.kapua.transport.message.jms.JmsTopic;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Jms message utility class
 * 
 * @since 1.0
 * 
 */
// TODO add translator caching
public class JmsUtil
{

    public static final Logger logger = LoggerFactory.getLogger(JmsUtil.class);

    @SuppressWarnings("rawtypes")
    private final static Map<String, Translator<JmsMessage, DeviceMessage>>   translatorFromJmsMap = new HashMap<String, Translator<JmsMessage, DeviceMessage>>();
    @SuppressWarnings("rawtypes")
    private final static Map<String, Translator<DeviceMessage, KapuaMessage>> translatorToKapuaMap = new HashMap<String, Translator<DeviceMessage, KapuaMessage>>();

    @SuppressWarnings("rawtypes")
    private final static Map<String, Translator<KapuaMessage, DeviceMessage>> translatorFromKapuaMap = new HashMap<String, Translator<KapuaMessage, DeviceMessage>>();
    @SuppressWarnings("rawtypes")
    private final static Map<String, Translator<DeviceMessage, JmsMessage>>   translatorToJmsMap     = new HashMap<String, Translator<DeviceMessage, JmsMessage>>();

    /**
     * Return the topic for the message's destination
     * 
     * @param jmsMessage
     * @return
     * @throws JMSException
     */
    public static String getJmsTopic(ActiveMQMessage jmsMessage) throws JMSException
    {
        String jmsTopic = null;
        if (jmsMessage.getDestination().isTopic()) {
            jmsTopic = ((Topic) jmsMessage.getJMSDestination()).getTopicName().substring(AclConstants.VT_TOPIC_PREFIX.length());
        }
        else if (jmsMessage.getDestination().isQueue()) {
            jmsTopic = jmsMessage.getStringProperty(MessageConstants.PROPERTY_ORIGINAL_TOPIC);
        }
        else {
            logger.warn("jmsMessage destination is not a Topic or Queue: {}", jmsMessage.getDestination());
        }
        return jmsTopic;
    }

    /**
     * Convert a {@link BytesMessage} to {@link CamelKapuaMessage}
     * 
     * @param jmsMessage
     * @throws JMSException
     * @throws KapuaException
     */
    public static CamelKapuaMessage<?> convertToKapuaMessage(ConnectorDescriptor connectorDescriptor, MESSAGE_TYPE messageType, BytesMessage jmsMessage, KapuaId connectionId)
        throws JMSException, KapuaException
    {
        String jmsTopic = jmsMessage.getStringProperty(MessageConstants.PROPERTY_ORIGINAL_TOPIC);
        Date queuedOn = new Date(jmsMessage.getLongProperty(MessageConstants.PROPERTY_ENQUEUED_TIMESTAMP));
        return convertToKapuaMessage(connectorDescriptor, connectorDescriptor.getDeviceClass(messageType), connectorDescriptor.getKapuaClass(messageType), jmsMessage, jmsTopic, queuedOn, connectionId);
    }

    /**
     * Convert a {@link BytesMessage} to {@link KapuaMessage}
     * 
     * this code
     * if (jmsMessage.getBodyLength() > 0) {
     * payload = new byte[(int) jmsMessage.getBodyLength()];
     * jmsMessage.readBytes(payload);
     * }
     * with camel doesn't work. The call getBodyLength returns the correct message size but the read call reads an empty array (-1 is returned).
     * The following code return the payload evaluated.
     * ((ActiveMQMessage)jmsMessage).getContent().data
     * so we modify the method assuming that camel converter called this utility method with a byte[] representing the jms body message
     * see {@link KapuaConverter}
     * 
     * TODO check the code with huge messages
     * 
     * @param jmsMessage
     * @throws JMSException
     * @throws KapuaException
     * @throws KapuaInvalidTopicException
     */
    @SuppressWarnings({ "unchecked", "rawtypes" })
    private static CamelKapuaMessage<?> convertToKapuaMessage(ConnectorDescriptor connectorDescriptor, Class<DeviceMessage<?, ?>> deviceMessageType, Class<KapuaMessage<?, ?>> kapuaMessageType, BytesMessage jmsMessage, String jmsTopic,
                                                              Date queuedOn, KapuaId connectionId)
        throws JMSException, KapuaException
    {
        byte[] payload = null;
        // TODO JMS message have no size limits!
        if (jmsMessage.getBodyLength() > 0) {
            payload = new byte[(int) jmsMessage.getBodyLength()];
            int readBytes = jmsMessage.readBytes(payload);
            logger.debug("Message conversion... {} bytes read!", readBytes);
        }
        KapuaMessage kapuaMessage = convertToKapuaMessage(deviceMessageType, kapuaMessageType, payload, jmsTopic, queuedOn, connectionId);
        return new CamelKapuaMessage(kapuaMessage, connectionId, connectorDescriptor);
    }

    /**
     * Convert raw byte[] message to {@link CamelKapuaMessage}
     * 
     * @param connectorDescriptor
     * @param messageType
     * @param messageBody
     * @param jmsTopic
     * @param queuedOn
     * @param connectionId
     * @return
     * @throws KapuaException
     */
    @SuppressWarnings({ "rawtypes", "unchecked" })
    public static CamelKapuaMessage<?> convertToCamelKapuaMessage(ConnectorDescriptor connectorDescriptor, MESSAGE_TYPE messageType, byte[] messageBody, String jmsTopic, Date queuedOn, KapuaId connectionId)
        throws KapuaException
    {
        KapuaMessage kapuaMessage = convertToKapuaMessage(connectorDescriptor.getDeviceClass(messageType), connectorDescriptor.getKapuaClass(messageType), messageBody, jmsTopic, queuedOn, connectionId);
        return new CamelKapuaMessage(kapuaMessage, connectionId, connectorDescriptor);
    }

    /**
     * Convert raw byte[] message to {@link KapuaMessage}
     * 
     * @param deviceMessageType
     * @param kapuaMessageType
     * @param messageBody
     * @param jmsTopic
     * @param queuedOn
     * @param connectionId
     * @return
     * @throws KapuaException
     */
    @SuppressWarnings("rawtypes")
    private static KapuaMessage convertToKapuaMessage(Class<DeviceMessage<?, ?>> deviceMessageType, Class<KapuaMessage<?, ?>> kapuaMessageType, byte[] messageBody, String jmsTopic, Date queuedOn, KapuaId connectionId)
        throws KapuaException
    {
        Translator<JmsMessage, DeviceMessage<?, ?>> translatorFromJms = null;// = translatorFromJmsMap.get(connectorDescriptor.getDeviceProtocolName());
        if (translatorFromJms == null) {
            // lookup
            translatorFromJms = Translator.getTranslatorFor(JmsMessage.class, deviceMessageType);// birth ...
            // translatorFromJmsMap.put(protocol, translatorFromJms);
        }
        DeviceMessage deviceMessage = translatorFromJms.translate(new JmsMessage(new JmsTopic(jmsTopic), queuedOn, new JmsPayload(messageBody)));

        // second step.... from device dependent protocol (unknown) to Kapua
        Translator<DeviceMessage<?, ?>, KapuaMessage<?, ?>> translatorToKapua = null;// = translatorToKapuaMap.get(connectorDescriptor.getDeviceProtocolName());
        if (translatorToKapua == null) {
            // lookup
            translatorToKapua = Translator.getTranslatorFor(deviceMessageType, kapuaMessageType);
            // translatorToKapuaMap.put(protocol, translatorToKapua);
        }
        return translatorToKapua.translate(deviceMessage);
    }

    /**
     * Convert a {@link KapuaMessage} message to a {@link JmsMessage}
     * 
     * @param connectorDescriptor
     * @param messageType
     * @param kapuaMessage
     * @return
     * @throws KapuaException
     * @throws ClassNotFoundException
     */
    @SuppressWarnings("rawtypes")
    public static JmsMessage convertToJmsMessage(ConnectorDescriptor connectorDescriptor, MESSAGE_TYPE messageType, KapuaMessage kapuaMessage) throws KapuaException, ClassNotFoundException
    {
        // first step... from Kapua to device level
        Translator<KapuaMessage<?, ?>, DeviceMessage<?, ?>> translatorFromKapua = null; // translatorFromKapuaMap.get(protocol);
        if (translatorFromKapua == null) {
            // lookup
            translatorFromKapua = Translator.getTranslatorFor(connectorDescriptor.getKapuaClass(messageType), connectorDescriptor.getDeviceClass(messageType));
            // translatorFromKapuaMap.put(protocol, translatorFromKapua);
        }
        DeviceMessage deviceMessage = translatorFromKapua.translate(kapuaMessage);

        // second step.... from device level to jms
        Translator<DeviceMessage<?, ?>, JmsMessage> translatorToJms = null; // translatorToJmsMap.get(protocol);
        if (translatorToJms == null) {
            // lookup
            translatorToJms = Translator.getTranslatorFor(connectorDescriptor.getDeviceClass(messageType), JmsMessage.class);
            // translatorToJmsMap.put(protocol, translatorToJms);
        }
        return translatorToJms.translate(deviceMessage);
    }

    // =========================================
    // wildcards conversion
    // =========================================
    /**
     * ActiveMQ translate wildcards from jms to mqtt
     * function ActiveMQ MQTT
     * separator . /
     * element * +
     * sub tree &gt; #
     * 
     * @param jmsTopic
     * @return
     */
    public static String convertJmsWildCardToMqtt(String jmsTopic)
    {
        String processedTopic = null;
        if (jmsTopic != null) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < jmsTopic.length(); i++) {
                sb.append(convertWildcardJmsToMqtt(jmsTopic.charAt(i)));
            }
            processedTopic = sb.toString();
        }
        return processedTopic;
    }

    private static char convertWildcardJmsToMqtt(char c)
    {
        switch (c) {
            case '.':
                return '/';
            case '/':
                return '.';
            default:
                return c;
        }
    }

    /**
     * ActiveMQ translate wildcards from jms to mqtt
     * function ActiveMQ MQTT
     * separator . /
     * element * +
     * sub tree &gt; #
     * 
     * @param mqttTopic
     * @return
     */
    public static String convertMqttWildCardToJms(String mqttTopic)
    {
        String processedTopic = null;
        if (mqttTopic != null) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < mqttTopic.length(); i++) {
                sb.append(convertWildcardMqttToJms(mqttTopic.charAt(i)));
            }
            processedTopic = sb.toString();
        }
        return processedTopic;
    }

    private static char convertWildcardMqttToJms(char c)
    {
        switch (c) {
            case '.':
                return '/';
            case '/':
                return '.';
            default:
                return c;
        }
    }

}
