package sd1920.trab1.server.serverUtils;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.logging.Logger;

import javax.ws.rs.ProcessingException;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.Entity;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.GenericType;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.xml.ws.Service;
import javax.xml.ws.WebServiceException;

import org.glassfish.jersey.client.ClientConfig;

import javax.xml.ws.BindingProvider;
import com.sun.xml.ws.client.BindingProviderProperties;

import sd1920.trab1.api.Message;
import sd1920.trab1.api.Discovery.DomainInfo;
import sd1920.trab1.api.rest.MessageServiceRest;
import sd1920.trab1.api.soap.MessageServiceSoap;
import sd1920.trab1.api.soap.MessagesException;
import sd1920.trab1.server.rest.resources.MessageResourceRest;

public class RequestHandler implements Runnable {

    // DUVIDA: Um client para todos os requests é seguro?
    protected static Client client;
    private ServerMessageUtils utils;
    private static Logger Log = ServerMessageUtils.Log;

    private BlockingQueue<Request> requests;

    public RequestHandler(ClientConfig config, ServerMessageUtils utils) {
        client = ClientBuilder.newClient(config);

        this.utils = utils;

        this.requests = new LinkedBlockingQueue<>();
    }

    public void addRequest(Request request) {
        this.requests.add(request);
    }

    public boolean execPostRequest(PostRequest request) {
        Response r = null;
        List<String> failedDeliveries = null;
        DomainInfo uri = request.getUri();
        String domain = request.getDomain();
        Message msg = request.getMessage();

        if (uri.isRest()) {

            WebTarget target = client.target(uri.getUri()).path(MessageServiceRest.PATH).path("mbox");

            try {
                r = target.request().accept(MediaType.APPLICATION_JSON)
                        .post(Entity.entity(msg, MediaType.APPLICATION_JSON));
            } catch (ProcessingException e) {
                Log.info("forwardMessage: Failed to forward message to " + domain + ". Retrying...");
                return false;
            }

            failedDeliveries = r.readEntity(new GenericType<List<String>>() {
            });
        } else {
            MessageServiceSoap msgService = null;

            try {
                Service service = Service.create(new URL(uri.getUri() + ServerMessageUtils.MESSAGES_WSDL),
                        ServerMessageUtils.MESSAGE_QNAME);
                msgService = service.getPort(MessageServiceSoap.class);
            } catch (MalformedURLException e) {
                Log.info("forwardMessage: Bad Url");
                return false;
            } catch (WebServiceException e) {
                Log.info("forwardMessage: Failed to forward message to " + domain + ".");
                return false;
            }

            ((BindingProvider) msgService).getRequestContext().put(BindingProviderProperties.CONNECT_TIMEOUT,
                    ServerMessageUtils.TIMEOUT);
            ((BindingProvider) msgService).getRequestContext().put(BindingProviderProperties.REQUEST_TIMEOUT,
                    ServerMessageUtils.TIMEOUT);

            try {
                failedDeliveries = msgService.postForwardedMessage(msg);
            } catch (WebServiceException wse) {
                Log.info("forwardMessage: Communication error. Retrying...");

                return false;
            }
        }

        String senderName = ServerMessageUtils.getSenderCanonicalName(msg.getSender());
        for (String recipient : failedDeliveries) {
            utils.saveErrorMessages(senderName, recipient, msg);
        }

        return true;
    }

    public boolean execDeleteRequest(DeleteRequest request) {
        DomainInfo uri = request.getUri();
        String domain = request.getDomain();
        String mid = request.getMid();

        if (uri.isRest()) {

            WebTarget target = client.target(uri.getUri()).path(MessageResourceRest.PATH).path("msg");

            try {
                target.path(mid).request().delete();
            } catch (ProcessingException e) {
                System.out.println("deleteFromDomains: Failed to redirect request to " + domain + ". Retrying...");
                Log.info("deleteFromDomains: Failed to redirect request to " + domain + ". Retrying...");
                return false;
            }

        } else {
            MessageServiceSoap msgService = null;

            try {
                Service service = Service.create(new URL(uri.getUri() + ServerMessageUtils.MESSAGES_WSDL),
                        ServerMessageUtils.MESSAGE_QNAME);
                msgService = service.getPort(MessageServiceSoap.class);
            } catch (MalformedURLException e) {
                Log.info("forwardMessage: Bad Url");
                return false;
            } catch (WebServiceException e) {
                Log.info("forwardMessage: Failed to forward message to " + domain + ".");
                return false;
            }

            ((BindingProvider) msgService).getRequestContext().put(BindingProviderProperties.CONNECT_TIMEOUT,
                    ServerMessageUtils.TIMEOUT);
            ((BindingProvider) msgService).getRequestContext().put(BindingProviderProperties.REQUEST_TIMEOUT,
                    ServerMessageUtils.TIMEOUT);

            try {
                msgService.deleteForwardedMessage(Long.valueOf(mid));
            } catch (MessagesException me) {
                Log.info("forwardMessage: Error, could not send the message.");
            } catch (WebServiceException wse) {
                Log.info("forwardMessage: Communication error. Retrying...");
                return false;
            }

        }

        return true;
    }

    @Override
    public void run() {
        for (;;) {
            Request r = null;

            try {
                r = this.requests.take();
            } catch (InterruptedException e1) {
                e1.printStackTrace();
            }
            System.out.println("GOT ONE");

            while (true) {
                if (r instanceof PostRequest ? this.execPostRequest((PostRequest) r)
                        : this.execDeleteRequest((DeleteRequest) r)) {
                    Log.info("RequestHandler: Successfully completed request to domain " + r.getDomain()
                            + ". More successful than i'll ever be!");
                    break;
                } else {
                    Log.info("RequestHandler: Couldn't contact other domain " + r.getDomain() + ". I SLEEP...");
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            }   
            
        }
    }

}
