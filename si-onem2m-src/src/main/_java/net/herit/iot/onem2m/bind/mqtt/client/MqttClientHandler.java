package net.herit.iot.onem2m.bind.mqtt.client;

import java.security.KeyStore;
import java.security.SecureRandom;
import java.util.Arrays;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;

import net.herit.iot.message.onem2m.OneM2mRequest;
import net.herit.iot.message.onem2m.OneM2mResponse;
import net.herit.iot.onem2m.bind.api.ResponseListener;
import net.herit.iot.onem2m.bind.mqtt.api.MqttServerListener;
import net.herit.iot.onem2m.bind.mqtt.codec.MqttRequestCodec;
import net.herit.iot.onem2m.bind.mqtt.codec.MqttResponseCodec;
import net.herit.iot.onem2m.bind.mqtt.util.Utils;

import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttCallback;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.MqttTopic;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MqttClientHandler implements MqttCallback {
	private static MqttClientHandler instance = null;

	private Logger log = LoggerFactory.getLogger(MqttClientHandler.class);
	
	public final static String ONEM2M_TOPIC_ID = "/oneM2M";
	public final static String REQ_TOPIC_ID = "/req";
	public final static String RES_TOPIC_ID = "/resp";
	public final static String REQUEST_TOPIC_BASE = ONEM2M_TOPIC_ID + REQ_TOPIC_ID;
	public final static String RESPONSE_TOPIC_BASE = ONEM2M_TOPIC_ID + RES_TOPIC_ID;

	private String subscribeTopic4Req;
	private String subscribeTopic4Res;
	private String clientID;

	private MqttClient mqttClient;
	private MqttConnectOptions connOpt;
	private MqttServerListener serverListener;
	private ResponseListener responseListener;

	private int keepAliveInterval = 30;
	private int subQoS = 1;
	private int pubQoS = 1;

	public static MqttClientHandler getInstance(String CES_ID) {
		if (instance == null) {
			instance = new MqttClientHandler(CES_ID);
		}

		return instance;
	}

	public static MqttClientHandler getInstance() {
		return instance;
	}

	private MqttClientHandler(String clientId) {
		this.clientID = Utils.getTopicID(clientId);
	}
	
	public void setListener(MqttServerListener listener) {
		this.serverListener = listener;
	}
	
	public void setListener(ResponseListener listener) {
		this.responseListener = listener;
	}

	public void connect(String brokerURL, boolean ssl) throws Exception {
		connect(brokerURL, clientID, null, ssl);
	}

	public void connect(String brokerURL, String userName, String password, boolean ssl) throws Exception {
		MqttConnectOptions connOpt = new MqttConnectOptions();

		connOpt.setCleanSession(false);
		connOpt.setKeepAliveInterval(keepAliveInterval);

		if (userName != null) {
			connOpt.setUserName(userName);
		}
		if  (password != null) {
			connOpt.setPassword(password.toCharArray());
		}

//		if(ssl) {
//			SSLContext sslContext = SSLContext.getInstance("SSL");
//			TrustManagerFactory trustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
//			KeyStore keyStore = readKeyStore();
//			trustManagerFactory.init(keyStore);
//			sslContext.init(null, trustManagerFactory.getTrustManagers(), new SecureRandom());
//			 
//			connOpt.setSocketFactory(sslContext.getSocketFactory());
//		}
		
		mqttClient = new MqttClient(brokerURL, clientID);
		mqttClient.setCallback(this);
		mqttClient.connect(connOpt);

		log.debug("[MqttClientHandler.connect] connected! CES_ID : "+clientID+", BrokerURL : " + brokerURL);

		setSubscribe();
	}

	public void deconnect() throws Exception 	{
		mqttClient.disconnect();
	}

	public String getClientId() {
		return this.clientID;
	}

	@Override
	public void connectionLost(Throwable arg0) {
		// TODO Auto-generated method stub

	}

	@Override
	public void deliveryComplete(IMqttDeliveryToken token) {
		// TODO Auto-generated method stub
		log.debug("[MqttClientHandler.deliveryComplete] deliveryComplete, topic : " +  Arrays.toString(token.getTopics()));

		if (serverListener != null)
		{
			serverListener.completedMqttDelivery(token.getMessageId());
		}
	}

	@Override
	public void messageArrived(String topic, MqttMessage message) throws Exception {
		// TODO Auto-generated method stub
		log.debug("[MqttClientHandler.messageArrived] messageArrived, topic : " + topic);

		try {

			byte[] payloadMessage = message.getPayload();
			log.debug("RCVD: " + new String(payloadMessage, "UTF-8"));
			
			if(topic.startsWith(REQUEST_TOPIC_BASE)) {
				String[] IDs = Utils.getOneM2mIDsFromTopic(topic, REQUEST_TOPIC_BASE);

				OneM2mRequest reqMessage = MqttRequestCodec.decode(payloadMessage);
				reqMessage.setCredentialID(IDs[0]);
				if (serverListener != null) {
					serverListener.receiveMqttMessage(reqMessage);
				}
			} else if(topic.startsWith(RESPONSE_TOPIC_BASE))  {
				OneM2mResponse resMessage = MqttResponseCodec.decode(payloadMessage);
				if (responseListener != null) {
					responseListener.receiveResponse(resMessage);
				}
			}
//			else {
//					log.warn("Not subscribed topic.(" + topic + ")");
//				}
//			}
		}
		catch (Exception e) {
			log.debug("Handled exception", e);
		}
	}

	private void setSubscribe() throws Exception {
		subscribeTopic4Req = REQUEST_TOPIC_BASE + "/+/" + clientID;
		subscribeTopic4Res = RESPONSE_TOPIC_BASE + "/" + clientID + "/+";
		mqttClient.subscribe(subscribeTopic4Req, subQoS);
		log.debug("SUBSCRIBED. TOPIC = " + subscribeTopic4Req);
		Thread.sleep(50);
		mqttClient.subscribe(subscribeTopic4Res, subQoS);
		log.debug("SUBSCRIBED. TOPIC = " + subscribeTopic4Res);
	}
	
	private void unsetSubscribe() throws Exception {
		mqttClient.unsubscribe(subscribeTopic4Res);
	}
	
	public int sendReqMessage(OneM2mRequest reqMessage) throws Exception {
		return sendReqMessage(reqMessage.getTo(), reqMessage);
	}
	
	public int sendResMessage(OneM2mResponse resMessage) throws Exception {
		return sendResMessage(resMessage.getTo(), resMessage);
	}
	
	/*
	 * targetID: 
	 */
	public int sendReqMessage(String targetID, OneM2mRequest reqMessage) throws Exception {
		targetID = Utils.getTopicID(targetID);
		if(targetID == null) throw new Exception("Invalid targetID");

		String strTopic = REQUEST_TOPIC_BASE + "/" + clientID + "/" + targetID;
	
		byte[] byteContents = MqttRequestCodec.encode(reqMessage);
		
		return publish(strTopic, byteContents);
		
	}
	
	public int sendResMessage(String targetID, OneM2mResponse resMessage) throws Exception {
		targetID = Utils.getTopicID(targetID);
		if(targetID == null) throw new Exception("Invalid targetID");
		
		String strTopic = RESPONSE_TOPIC_BASE + "/" + targetID + "/" + clientID;
		
		byte[] byteContents = MqttResponseCodec.encode(resMessage);
		
		return publish(strTopic, byteContents);
	}
	
	public int sendReqMessage(String targetID, String message) throws Exception {
		targetID = Utils.getTopicID(targetID);
		if(targetID == null) throw new Exception("Invalid targetID");
		
		String strTopic = REQUEST_TOPIC_BASE + "/" + clientID + "/" + targetID;

		return publish(strTopic, message.getBytes());

	}
	
	public int sendResMessage(String targetID, String message) throws Exception {
		targetID = Utils.getTopicID(targetID);
		if(targetID == null) throw new Exception("Invalid targetID");
		
		String strTopic = RESPONSE_TOPIC_BASE + "/" + targetID + "/" + clientID;
		
		return publish(strTopic, message.getBytes());
		
	}
	
 
	private int publish(String strTopic, byte[] contents) throws Exception {
		
		log.debug("PUBLISH] topic=" + strTopic + ", contents=" + new String(contents, "UTF-8"));
		
		MqttTopic mqttTopic = mqttClient.getTopic(strTopic);
		
		MqttMessage message = new MqttMessage(contents);
		message.setQos(pubQoS);
		message.setRetained(false);

		MqttDeliveryToken token = null;
		token = mqttTopic.publish(message);
		token.waitForCompletion();

		log.debug("[MqttClientHandler.publish] publish. topic : " + strTopic);
		
		return token.getMessageId();
	}
	
}
