package com.github.gilbertotorrezan.http2;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.logging.Logger;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.annotation.WebFilter;
import javax.servlet.http.HttpServletRequest;

import org.eclipse.jetty.http.HttpField;
import org.eclipse.jetty.http.HttpFields;
import org.eclipse.jetty.http.HttpHeader;
import org.eclipse.jetty.http.HttpMethod;
import org.eclipse.jetty.http.HttpVersion;
import org.eclipse.jetty.server.PushBuilder;
import org.eclipse.jetty.server.Request;

@WebFilter(filterName = "Http2PushListFilter", asyncSupported = true)
public class Http2PushListFilter implements Filter {

    private static final String WHEN_PREFIX = "when ";

    private static final Logger LOG = Logger
            .getLogger(Http2PushListFilter.class.getName());

    private Map<String, List<String>> filesToPush = new HashMap<>();

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
        InputStream stream = filterConfig.getServletContext()
                .getResourceAsStream("/WEB-INF/http2-push.txt");
        if (stream == null) {
            LOG.warning(
                    "No \"http2-push.txt\" file was found inside WEB-INF. No files will be pushed.");
        } else {
            int count = 0;
            try (Scanner scanner = new Scanner(stream, "UTF-8")
                    .useDelimiter(System.lineSeparator())) {

                List<String> endpoints = new LinkedList<>();

                while (scanner.hasNext()) {
                    String next = scanner.next().trim();
                    if (next.isEmpty() || next.startsWith("#")) {
                        continue;
                    }

                    if (next.toLowerCase().startsWith(WHEN_PREFIX)) {
                        String urls = next.substring(WHEN_PREFIX.length() + 1,
                                next.length());

                        String[] split = urls.split("\\s+");
                        endpoints.clear();
                        Collections.addAll(endpoints, split);
                        continue;
                    }

                    if (endpoints.isEmpty()) {
                        endpoints.add("");
                    }

                    for (String endpoint : endpoints) {
                        List<String> list = filesToPush.get(endpoint);
                        if (list == null) {
                            list = new LinkedList<>();
                            filesToPush.put(endpoint, list);
                        }
                        list.add(next);
                        count++;
                    }
                }
                LOG.info(Http2PushListFilter.class.getName() + " initialized - "
                        + count + " files are set to be pushed.");
            }
        }
    }

    @Override
    public void doFilter(ServletRequest req, ServletResponse resp,
            FilterChain chain) throws IOException, ServletException {

        HttpServletRequest request = (HttpServletRequest) req;
        Request jettyRequest = Request.getBaseRequest(request);

        if (HttpVersion.fromString(request.getProtocol()).getVersion() < 20
                || !HttpMethod.GET.is(request.getMethod())
                || filesToPush.isEmpty() || !jettyRequest.isPushSupported()) {
            chain.doFilter(req, resp);
            return;
        }

        String path = request.getPathInfo();
        if (path == null) {
            path = "";
        }

        List<String> toPush = filesToPush.get(path);
        if (!path.isEmpty() && filesToPush.get("") != null) {
            toPush = new LinkedList<>(filesToPush.get(""));
            toPush.addAll(filesToPush.get(path));
        }

        if (toPush == null || toPush.isEmpty()) {
            chain.doFilter(req, resp);
            return;
        }

        HttpFields fields = jettyRequest.getHttpFields();
        boolean conditional = false;
        loop: for (int i = 0; i < fields.size(); i++) {
            HttpField field = fields.getField(i);
            HttpHeader header = field.getHeader();
            if (header == null)
                continue;

            switch (header) {
            case IF_MATCH:
            case IF_MODIFIED_SINCE:
            case IF_NONE_MATCH:
            case IF_UNMODIFIED_SINCE:
                conditional = true;
                break loop;
            default:
                break;
            }
        }

        if (!conditional) {
            pushList(jettyRequest, toPush);
        }

        chain.doFilter(request, resp);
    }

    private void pushList(Request jettyRequest, List<String> list) {
        PushBuilder pushBuilder = jettyRequest.getPushBuilder();

        for (String file : list) {
            LOG.fine("Pushing " + file);
            pushBuilder.path(file).push();
        }
    }

    @Override
    public void destroy() {
        filesToPush.clear();
    }

}
