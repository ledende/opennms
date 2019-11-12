/*******************************************************************************
 * This file is part of OpenNMS(R).
 *
 * Copyright (C) 2014 The OpenNMS Group, Inc.
 * OpenNMS(R) is Copyright (C) 1999-2014 The OpenNMS Group, Inc.
 *
 * OpenNMS(R) is a registered trademark of The OpenNMS Group, Inc.
 *
 * OpenNMS(R) is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License,
 * or (at your option) any later version.
 *
 * OpenNMS(R) is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with OpenNMS(R).  If not, see:
 *      http://www.gnu.org/licenses/
 *
 * For more information contact:
 *     OpenNMS(R) Licensing <license@opennms.org>
 *     http://www.opennms.org/
 *     http://www.opennms.com/
 *******************************************************************************/

package org.opennms.netmgt.poller.monitors;

import static org.opennms.core.web.HttpClientWrapperConfigHelper.setUseSystemProxyIfDefined;

import java.io.InputStream;
import java.net.InetAddress;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.charset.UnsupportedCharsetException;
import java.util.Map;
import java.util.regex.Pattern;

import org.apache.commons.io.IOUtils;
import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.ParseException;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.message.BasicHeader;
import org.opennms.core.utils.InetAddressUtils;
import org.opennms.core.utils.ParameterMap;
import org.opennms.core.utils.TimeoutTracker;
import org.opennms.core.web.HttpClientWrapper;
import org.opennms.netmgt.poller.Distributable;
import org.opennms.netmgt.poller.DistributionContext;
import org.opennms.netmgt.poller.MonitoredService;
import org.opennms.netmgt.poller.PollStatus;
import org.opennms.netmgt.poller.monitors.support.ParameterSubstitutingMonitor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Strings;

/**
 * This class is designed to be used by the service poller framework to test the
 * availability of a url by posting a generic payload and evaulating the http response code and banner. The class
 * implements the ServiceMonitor interface that allows it to be used along with
 * other plug-ins by the service poller framework.
 * 
 * @author <A HREF="mailto:jeffg@opennms.org">Jeff Gehlbach</A>
 * @author <A HREF="mailto:cliles@capario.com">Chris Liles</A>
 * @author <A HREF="http://www.opennms.org/">OpenNMS</a>
 */

@Distributable(DistributionContext.DAEMON)
final public class HttpPostMonitor extends ParameterSubstitutingMonitor {

    /**
     * Default port.
     */
    private static final int DEFAULT_PORT = 80;

    /**
     * Default retries.
     */
    private static final int DEFAULT_RETRY = 0;

    /**
     * Default timeout. Specifies how long (in milliseconds) to block waiting
     * for data from the monitored interface.
     */
    private static final int DEFAULT_TIMEOUT = 3000;

    public static final String DEFAULT_MIMETYPE = "text/xml";
    public static final String DEFAULT_CHARSET = StandardCharsets.UTF_8.name();
    public static final String DEFAULT_URI = "/";
    public static final String DEFAULT_SCHEME = "http";
    public static final boolean DEFAULT_SSLFILTER = false;

    public static final String PARAMETER_SCHEME = "scheme";
    public static final String PARAMETER_PORT = "port";
    public static final String PARAMETER_URI = "uri";
    public static final String PARAMETER_PAYLOAD = "payload";
    public static final String PARAMETER_MIMETYPE = "mimetype";
    public static final String PARAMETER_CHARSET = "charset";
    public static final String PARAMETER_BANNER = "banner";
    public static final String PARAMETER_SSLFILTER = "usesslfiler";

    public static final String PARAMETER_USERNAME = "auth-username";
    public static final String PARAMETER_PASSWORD = "auth-password";

    private static final Logger LOG = LoggerFactory.getLogger(HttpPostMonitor.class);
    private static final Pattern HEADER_PATTERN = Pattern.compile("header[0-9]+$");

    /**
     * {@inheritDoc}
     *
     * Poll the specified address for service availability.
     *
     * During the poll an attempt is made to execute the named method (with optional input) connect on the specified port. If
     * the exec on request is successful, the banner line generated by the
     * interface is parsed and if the banner text indicates that we are talking
     * to Provided that the interface's response is valid we set the service
     * status to SERVICE_AVAILABLE and return.
     */
    public PollStatus poll(MonitoredService svc, Map<String, Object> parameters) {
        // Process parameters
        TimeoutTracker tracker = new TimeoutTracker(parameters, DEFAULT_RETRY, DEFAULT_TIMEOUT);

        // Port
        int port = ParameterMap.getKeyedInteger(parameters, PARAMETER_PORT, DEFAULT_PORT);

        //URI
        String strURI = resolveKeyedString(parameters, PARAMETER_URI, DEFAULT_URI);

        //Username
        String strUser = resolveKeyedString(parameters, PARAMETER_USERNAME, null);

        //Password
        String strPasswd = resolveKeyedString(parameters, PARAMETER_PASSWORD, null);

        //BannerMatch
        String strBannerMatch = resolveKeyedString(parameters, PARAMETER_BANNER, null);

        //Scheme
        String strScheme = ParameterMap.getKeyedString(parameters, PARAMETER_SCHEME, DEFAULT_SCHEME);

        //Payload
        String strPayload = ParameterMap.getKeyedString(parameters, PARAMETER_PAYLOAD, null);

        //Mimetype
        String strMimetype = ParameterMap.getKeyedString(parameters, PARAMETER_MIMETYPE, DEFAULT_MIMETYPE);

        //Charset
        String strCharset = ParameterMap.getKeyedString(parameters, PARAMETER_CHARSET, DEFAULT_CHARSET);

        //SSLFilter
        boolean boolSSLFilter = ParameterMap.getKeyedBoolean(parameters, PARAMETER_SSLFILTER, DEFAULT_SSLFILTER);

        // Get the address instance.
        InetAddress ipAddr = svc.getAddress();

        final String hostAddress = InetAddressUtils.str(ipAddr);

        LOG.debug("poll: address = {}, port = {}, {}", hostAddress, port, tracker);

        // Give it a whirl
        PollStatus serviceStatus = PollStatus.unavailable();

        for (tracker.reset(); tracker.shouldRetry() && !serviceStatus.isAvailable(); tracker.nextAttempt()) {
            HttpClientWrapper clientWrapper = null;
            try {
                tracker.startAttempt();

                clientWrapper = HttpClientWrapper.create()
                        .setConnectionTimeout(tracker.getSoTimeout())
                        .setSocketTimeout(tracker.getSoTimeout())
                        .setRetries(DEFAULT_RETRY);

                if (boolSSLFilter)  {
                    clientWrapper.trustSelfSigned(strScheme);
                }
                HttpEntity postReq;

                if (strUser != null && strPasswd != null) {
                    clientWrapper.addBasicCredentials(strUser, strPasswd);
                }

                setUseSystemProxyIfDefined(clientWrapper, parameters);

                try {
                    postReq = new StringEntity(strPayload, ContentType.create(strMimetype, strCharset));
                } catch (final UnsupportedCharsetException e) {
                    serviceStatus = PollStatus.unavailable("Unsupported encoding encountered while constructing POST body " + e);
                    break;
                }

                URIBuilder ub = new URIBuilder();
                ub.setScheme(strScheme);
                ub.setHost(hostAddress);
                ub.setPort(port);
                ub.setPath(strURI);

                LOG.debug("HttpPostMonitor: Constructed URL is {}", ub);

                HttpPost post = new HttpPost(ub.build());

                for (final String key : parameters.keySet()) {
                    if (HEADER_PATTERN.matcher(key).matches()) {
                        post.setHeader(getHeader(ParameterMap.getKeyedString(parameters, key, null)));
                    }
                }

                post.setEntity(postReq);
                CloseableHttpResponse response = clientWrapper.execute(post);

                LOG.debug("HttpPostMonitor: Status Line is {}", response.getStatusLine());

                if (response.getStatusLine().getStatusCode() > 399) {
                    LOG.info("HttpPostMonitor: Got response status code {}", response.getStatusLine().getStatusCode());
                    LOG.debug("HttpPostMonitor: Received server response: {}", response.getStatusLine());
                    LOG.debug("HttpPostMonitor: Failing on bad status code");
                    serviceStatus = PollStatus.unavailable("HTTP(S) Status code " + response.getStatusLine().getStatusCode());
                    break;
                }

                LOG.debug("HttpPostMonitor: Response code is valid");
                double responseTime = tracker.elapsedTimeInMillis();

                HttpEntity entity = response.getEntity();
                InputStream responseStream = entity.getContent();
                String Strresponse = IOUtils.toString(responseStream);

                if (Strresponse == null)
                    continue;

                LOG.debug("HttpPostMonitor: banner = {}", Strresponse);
                LOG.debug("HttpPostMonitor: responseTime= {}ms", responseTime);

                //Could it be a regex?
                if (!Strings.isNullOrEmpty(strBannerMatch) && strBannerMatch.startsWith("~")) {
                    if (!Strresponse.matches(strBannerMatch.substring(1))) {
                        serviceStatus = PollStatus.unavailable("Banner does not match Regex '"+strBannerMatch+"'");
                        break;
                    }
                    else {
                        serviceStatus = PollStatus.available(responseTime);
                    }
                }
                else {
                    if (Strresponse.indexOf(strBannerMatch) > -1) {
                        serviceStatus = PollStatus.available(responseTime);
                    }
                    else {
                        serviceStatus = PollStatus.unavailable("Did not find expected Text '"+strBannerMatch+"'");
                        break;
                    }
                }

            } catch (final URISyntaxException e) {
                final String reason = "URISyntaxException for URI: " + strURI + " " + e.getMessage();
                LOG.debug(reason, e);
                serviceStatus = PollStatus.unavailable(reason);
                break;
            } catch (final Exception e) {
                final String reason = "Exception: " + e.getMessage();
                LOG.debug(reason, e);
                serviceStatus = PollStatus.unavailable(reason);
                break;
            } finally {
                IOUtils.closeQuietly(clientWrapper);
            }
        }

        // return the status of the service
        return serviceStatus;
    }

    private Header getHeader(final String header) {
        if (Strings.isNullOrEmpty(header)) {
            throw new ParseException("Header is null or empty");
        }

        final String arr[] = header.split(":");

        if (arr.length != 2) {
            throw new ParseException("Invalid header: '" + header + "'");
        }

        return new BasicHeader(arr[0].trim(), arr[1].trim());
    }
}
