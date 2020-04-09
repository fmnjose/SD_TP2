package sd1920.trab1.server.serverUtils;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.List;
import java.util.concurrent.BlockingQueue;
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
        synchronized(this.requests){
            this.requests.add(request);
        }
    }

    public static List<String> processPostRequest(PostRequest request) throws ProcessingException, MalformedURLException, WebServiceException{
        Response r = null;
        List<String> failedDeliveries = null;
        DomainInfo uri = request.getUri();
        Message msg = request.getMessage();

        if (uri.isRest()) {
            System.out.println("MIAU");
            
            WebTarget target = client.target(uri.getUri()).path(MessageServiceRest.PATH).path("mbox");
            
            r = target.request().accept(MediaType.APPLICATION_JSON)
                .post(Entity.entity(msg, MediaType.APPLICATION_JSON));

            failedDeliveries = r.readEntity(new GenericType<List<String>>() {
            });
        } else {
            MessageServiceSoap msgService = null;

            System.out.println("CRIAR SERVIÇO");
            Service service = Service.create(new URL(uri.getUri() + ServerMessageUtils.MESSAGES_WSDL),
                    ServerMessageUtils.MESSAGE_QNAME);

            System.out.println("ANTES MSGSERVICE");
            msgService = service.getPort(MessageServiceSoap.class);
            
            
            
            ((BindingProvider) msgService).getRequestContext().put(BindingProviderProperties.CONNECT_TIMEOUT,
                    ServerMessageUtils.TIMEOUT);
            ((BindingProvider) msgService).getRequestContext().put(BindingProviderProperties.REQUEST_TIMEOUT,
                    ServerMessageUtils.TIMEOUT);

                    
            System.out.println("TENTAR FAZER FORWARDMESSAGE");
            failedDeliveries = msgService.postForwardedMessage(msg);
            System.out.println("TERMINEI");
        }
        return failedDeliveries;
    }

    public boolean execPostRequest(PostRequest request) {
       
        List<String> failedDeliveries = null;
        try{
            failedDeliveries = processPostRequest(request);
        }
        catch (ProcessingException e) {
            System.out.println("PROCESSING POST");
            Log.info("forwardMessage: Failed to forward message to " + request.getDomain() + ". Retrying...");
            return false;
        }
        catch (MalformedURLException e) {
            System.out.println("MALFORMED POST");
            Log.info("forwardMessage: Bad Url");
            return false;
        } 
        catch (WebServiceException e) {
            System.out.println("WEBSERVICE POST");
            Log.info("forwardMessage: Failed to forward message to " + request.getDomain() + ".");
            return true;
        }

        System.out.println("FALIED DELIVERIES: " + failedDeliveries.size());
        String senderName = ServerMessageUtils.getSenderCanonicalName(request.getMessage().getSender());
        for (String recipient : failedDeliveries) {
            utils.saveErrorMessages(senderName, recipient, request.getMessage());
        }

        return true;
    }

    public static void processDeleteRequest(DeleteRequest request) throws ProcessingException, 
                        MessagesException, WebServiceException, MalformedURLException {
    
        DomainInfo uri = request.getUri();
        String mid = request.getMid();

        if (uri.isRest()) {

            WebTarget target = client.target(uri.getUri()).path(MessageResourceRest.PATH).path("msg");

            target.path(mid).request().delete();
        } else {
            MessageServiceSoap msgService = null;
            
            Service service = Service.create(new URL(uri.getUri() + ServerMessageUtils.MESSAGES_WSDL),
                    ServerMessageUtils.MESSAGE_QNAME);
            msgService = service.getPort(MessageServiceSoap.class);
            

            ((BindingProvider) msgService).getRequestContext().put(BindingProviderProperties.CONNECT_TIMEOUT,
                    ServerMessageUtils.TIMEOUT);
            ((BindingProvider) msgService).getRequestContext().put(BindingProviderProperties.REQUEST_TIMEOUT,
                    ServerMessageUtils.TIMEOUT);

            
            msgService.deleteForwardedMessage(Long.valueOf(mid));
        }
    }

    public boolean execDeleteRequest(DeleteRequest request) {
        try {
            processDeleteRequest(request);
        } catch (ProcessingException e) {
            System.out.println("PROCESSING DELETE");
            Log.info("deleteFromDomains: Failed to redirect request to " + request.getDomain() + ". Retrying...");
            return false;
        } catch (MalformedURLException e) {
            System.out.println("MALFORMED DELETE");
            Log.info("deleteFromDomains: Bad Url");
            return false;
        } catch (WebServiceException e) {
            System.out.println("WEBSERVICE DELETE");
            Log.info("deleteFromDomains: Failed to forward message to " + request.getDomain() + ".");
            return true;
        } catch (MessagesException me) {
            Log.info("deleteFromDomains: Error, could not send the message.");
            return true;
        }

        System.out.println("SUCCESSFUL DELETE");

        return true;
    }

    @Override
    public void run() {
        System.out.println("REQUEST HANDLER COMEÇOU");
        for (;;) {
            Request r = null;

            try {
                r = this.requests.take();
            } catch (InterruptedException e1) {
                e1.printStackTrace();
            }

            while (true) {
                if (r instanceof PostRequest ? this.execPostRequest((PostRequest) r)
                        : this.execDeleteRequest((DeleteRequest) r)) {
                    
                    System.out.println("RequestHandler: Successfully completed request to domain " + r.getDomain()
                    + ". More successful than i'll ever be!");        
                    Log.info("RequestHandler: Successfully completed request to domain " + r.getDomain()
                            + ". More successful than i'll ever be!");
                    break;
                } else {
                    System.out.println("RequestHandler: Couldn't contact other domain " + r.getDomain() + ". I SLEEP...");
                    Log.info("RequestHandler: Couldn't contact other domain " + r.getDomain() + ". I SLEEP...");
                    try {
                        Thread.sleep(200);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            }   
            
        }
    }

}
