/*
 * The MIT License
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
package org.influxdata.client;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import javax.annotation.Nonnull;

import org.influxdata.client.domain.Document;
import org.influxdata.client.domain.DocumentCreate;
import org.influxdata.client.domain.DocumentListEntry;
import org.influxdata.client.domain.DocumentMeta;
import org.influxdata.client.domain.Label;
import org.influxdata.client.domain.LabelCreateRequest;
import org.influxdata.client.domain.Organization;
import org.influxdata.exceptions.BadRequestException;
import org.influxdata.exceptions.NotFoundException;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.platform.runner.JUnitPlatform;
import org.junit.runner.RunWith;

/**
 * @author Jakub Bednar (bednar@github) (25/03/2019 09:52)
 */
@RunWith(JUnitPlatform.class)
class ITTemplatesApiTest extends AbstractITClientTest {

    private TemplatesApi templatesApi;
    private Organization organization;

    @BeforeEach
    void setUp() {

        templatesApi = influxDBClient.getTemplatesApi();
        organization = findMyOrg();

        templatesApi.findTemplates(organization)
                .forEach(documentListEntry -> templatesApi.deleteTemplate(documentListEntry.getId()));
    }

    @Test
    void create() {

        LabelCreateRequest labelCreateRequest = new LabelCreateRequest();
        labelCreateRequest.setOrgID(organization.getId());
        labelCreateRequest.setName(generateName("label"));
        labelCreateRequest.putPropertiesItem("color", "red");
        labelCreateRequest.putPropertiesItem("priority", "top");

        Label label = influxDBClient.getLabelsApi().createLabel(labelCreateRequest);

        DocumentMeta meta = new DocumentMeta();
        meta.setName(generateName("document-name"));
        meta.setVersion("1");

        DocumentCreate documentCreate = new DocumentCreate();
        documentCreate.setMeta(meta);
        documentCreate.setOrgID(organization.getId());
        documentCreate.setContent("templates content");

        ArrayList<String> labels = new ArrayList<>();
        labels.add(label.getName());
        documentCreate.setLabels(labels);

        Document template = templatesApi.createTemplate(documentCreate);

        Assertions.assertThat(template).isNotNull();
        Assertions.assertThat(template.getId()).isNotBlank();
        Assertions.assertThat(template.getContent()).isEqualTo("templates content");
        Assertions.assertThat(template.getMeta()).isNotNull();
        Assertions.assertThat(template.getMeta().getName()).isEqualTo(meta.getName());
        Assertions.assertThat(template.getMeta().getVersion()).isEqualTo("1");
        Assertions.assertThat(template.getLinks()).isNotNull();
        Assertions.assertThat(template.getLinks().getSelf()).isEqualTo("/api/v2/documents/templates/" + template.getId());

        Assertions.assertThat(template.getLabels()).hasSize(1);
        Assertions.assertThat(template.getLabels().get(0).getName()).isEqualTo(labelCreateRequest.getName());
    }

    @Test
    void createEmpty() {

        Assertions.assertThatThrownBy(() -> templatesApi.createTemplate(new DocumentCreate()))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("missing document body");
    }

    @Test
    void notExistLabel() {

        DocumentCreate documentCreate = createDoc();

        ArrayList<String> labels = new ArrayList<>();
        labels.add(generateName("not_exists_label_"));
        documentCreate.setLabels(labels);

        Assertions.assertThatThrownBy(() -> templatesApi.createTemplate(documentCreate))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("label not found");
    }

    @Test
    void deleteTemplate() {

        DocumentCreate documentCreate = createDoc();

        Document createdTemplate = templatesApi.createTemplate(documentCreate);
        Assertions.assertThat(createdTemplate).isNotNull();

        Document foundTemplate = templatesApi.findTemplateByID(createdTemplate.getId());
        Assertions.assertThat(foundTemplate).isNotNull();

        // delete template
        templatesApi.deleteTemplate(createdTemplate);

        Assertions.assertThatThrownBy(() -> templatesApi.findTemplateByID(createdTemplate.getId()))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void findTemplateByID() {

        Document template = templatesApi.createTemplate(createDoc());

        Document templateByID = templatesApi.findTemplateByID(template.getId());

        Assertions.assertThat(templateByID).isNotNull();
        Assertions.assertThat(templateByID.getId()).isEqualTo(template.getId());
        Assertions.assertThat(templateByID.getMeta().getName()).isEqualTo(template.getMeta().getName());
    }

    @Test
    void findTemplateByIDNull() {

        Assertions.assertThatThrownBy(() -> templatesApi.findTemplateByID("020f755c3c082000"))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("document not found");
    }

    @Test
    void findTemplates() {

        Organization org = influxDBClient.getOrganizationsApi().createOrganization(generateName("org"));

        LabelCreateRequest labelCreateRequest = new LabelCreateRequest();
        labelCreateRequest.setOrgID(organization.getId());
        labelCreateRequest.setName(generateName("label"));
        labelCreateRequest.putPropertiesItem("color", "red");
        labelCreateRequest.putPropertiesItem("priority", "top");

        Label label = influxDBClient.getLabelsApi().createLabel(labelCreateRequest);

        List<DocumentListEntry> templates = templatesApi.findTemplates(org);
        Assertions.assertThat(templates).isEmpty();

        DocumentMeta meta = new DocumentMeta();
        meta.setName(generateName("document-name"));
        meta.setVersion("1");

        DocumentCreate documentCreate = new DocumentCreate();
        documentCreate.setMeta(meta);
        documentCreate.setOrgID(org.getId());
        documentCreate.setContent("templates content");
        documentCreate.setLabels(Arrays.asList(label.getName()));

        templatesApi.createTemplate(documentCreate);
        templates = templatesApi.findTemplates(org);
        Assertions.assertThat(templates).hasSize(1);

        DocumentListEntry entry = templates.get(0);
        Assertions.assertThat(entry.getId()).isNotBlank();
        Assertions.assertThat(entry.getMeta()).isNotNull();
        Assertions.assertThat(entry.getMeta().getVersion()).isEqualTo("1");
        Assertions.assertThat(entry.getMeta().getName()).isEqualTo(meta.getName());
        Assertions.assertThat(entry.getLinks()).isNotNull();
        Assertions.assertThat(entry.getLinks().getSelf()).isEqualTo("/api/v2/documents/templates/" + entry.getId());

        Assertions.assertThat(entry.getLabels()).hasSize(1);
        Assertions.assertThat(entry.getLabels().get(0).getName()).isEqualTo(labelCreateRequest.getName());

        //delete
        templatesApi.deleteTemplate(entry.getId());
    }

    @Test
    void findTemplatesNotFound() {

        Assertions.assertThatThrownBy(() -> templatesApi.findTemplates("020f755c3c082000"))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("organization name \"020f755c3c082000\" not found");
    }

    @Test
    void updateTemplate() {

        DocumentCreate documentCreate = createDoc();

        Document template = templatesApi.createTemplate(documentCreate);

        template
                .content("changed_content")
                .getMeta()
                .version("2")
                .name("changed_name.txt");

        Document updated = templatesApi.updateTemplate(template);

        Assertions.assertThat(updated).isNotNull();
        Assertions.assertThat(updated.getContent()).isEqualTo("changed_content");
        Assertions.assertThat(updated.getMeta()).isNotNull();
        Assertions.assertThat(updated.getMeta().getVersion()).isEqualTo("2");
        Assertions.assertThat(updated.getMeta().getName()).isEqualTo("changed_name.txt");
    }

    @Nonnull
    private DocumentCreate createDoc() {

        DocumentMeta meta = new DocumentMeta();
        meta.setName(generateName("document-name"));
        meta.setVersion("1");

        DocumentCreate documentCreate = new DocumentCreate();
        documentCreate.setMeta(meta);
        documentCreate.setOrgID(organization.getId());
        documentCreate.setContent("templates content");

        return documentCreate;
    }
}