/*
 * The MIT License
 *
 * Copyright 2017 Intuit Inc.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package com.intuit.karate.http.apache;

import com.intuit.karate.ScriptValue;
import com.intuit.karate.StringUtils;
import com.intuit.karate.http.HttpBody;
import com.intuit.karate.http.HttpUtils;
import com.intuit.karate.http.MultiPartItem;
import com.intuit.karate.http.MultiValuedMap;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.apache.http.HttpEntity;
import org.apache.http.NameValuePair;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.entity.mime.FormBodyPartBuilder;
import org.apache.http.entity.mime.MultipartEntityBuilder;
import org.apache.http.entity.mime.content.ContentBody;
import org.apache.http.entity.mime.content.InputStreamBody;
import org.apache.http.entity.mime.content.StringBody;
import org.apache.http.message.BasicNameValuePair;

/**
 *
 * @author pthomas3
 */
public class ApacheHttpUtils {
    
    private ApacheHttpUtils() {
        // only static methods
    }
    
    public static HttpBody toBody(HttpEntity entity) {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            entity.writeTo(baos);
            return HttpBody.bytes(baos.toByteArray(), entity.getContentType().getValue());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }        
    }
    
    public static HttpEntity getEntity(MultiValuedMap fields, String mediaType) {
        List<NameValuePair> list = new ArrayList<>(fields.size());
        for (Map.Entry<String, List> entry : fields.entrySet()) {
            String stringValue;
            List values = entry.getValue();
            if (values == null) {
                stringValue = null;
            } else if (values.size() == 1) {
                Object value = values.get(0);
                if (value == null) {
                    stringValue = null;
                } else if (value instanceof String) {
                    stringValue = (String) value;
                } else {
                    stringValue = value.toString();
                }
            } else {
                stringValue = StringUtils.join(values, ',');
            }
            list.add(new BasicNameValuePair(entry.getKey(), stringValue));
        }
        try {
            UrlEncodedFormEntity entity = new UrlEncodedFormEntity(list);
            entity.setContentType(mediaType);
            return entity;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
    
    public static ContentType createContentType(String mediaType) {
        StringUtils.Pair pair = HttpUtils.splitCharsetIfPresent(mediaType);
        ContentType ct = ContentType.create(pair.left);
        if (pair.right != null) {
            ct = ct.withCharset(pair.right);
        }
        return ct;
    }

    public static HttpEntity getEntity(List<MultiPartItem> items, String mediaType) {
        boolean hasNullName = false;
        for (MultiPartItem item : items) {
            if (item.getName() == null) {
                hasNullName = true;
                break;
            }
        }
        if (hasNullName) { // multipart/related
            String boundary = HttpUtils.generateMimeBoundaryMarker();
            String text = HttpUtils.multiPartToString(items, boundary);
            ContentType ct = createContentType(mediaType).withParameters(new BasicNameValuePair("boundary", boundary));
            return new StringEntity(text, ct);
        } else {
            MultipartEntityBuilder builder = MultipartEntityBuilder.create().setContentType(createContentType(mediaType));
            for (MultiPartItem item : items) {
                if (item.getValue() == null || item.getValue().isNull()) {
                    continue;
                }
                String name = item.getName();                
                if (name == null) {
                    // will never happen because we divert this flow to the home-made multi-part builder above
                    // builder.addPart(bodyPart);
                } else {
                    ScriptValue sv = item.getValue();
                    String ct = item.getContentType();                    
                    if (ct == null) {
                        ct = HttpUtils.getContentType(sv);
                    }                    
                    ContentType contentType = ContentType.create(ct);
                    FormBodyPartBuilder formBuilder = FormBodyPartBuilder.create().setName(name);
                    ContentBody contentBody;
                    String filename = item.getFilename();
                    if (filename != null) {
                        InputStream is = sv.getAsStream();
                        contentBody = new InputStreamBody(is, contentType, filename);
                    } else if (sv.isStream()) {
                        contentBody = new InputStreamBody(sv.getAsStream(), contentType);
                    } else {                    
                        contentBody = new StringBody(sv.getAsString(), contentType);
                    }
                    formBuilder = formBuilder.setBody(contentBody);
                    builder = builder.addPart(formBuilder.build());
                }
            }
            return builder.build();
        }
    }    
    
}
