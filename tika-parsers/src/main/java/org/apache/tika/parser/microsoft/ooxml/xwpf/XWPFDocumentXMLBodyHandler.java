/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.tika.parser.microsoft.ooxml.xwpf;


import java.util.Date;
import java.util.Map;

import org.apache.tika.parser.microsoft.ooxml.AbstractDocumentXMLBodyHandler;
import org.apache.tika.parser.microsoft.ooxml.ParagraphProperties;
import org.apache.tika.parser.microsoft.ooxml.RunProperties;
import org.apache.tika.utils.DateUtils;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;

/**
 * This class is intended to handle anything that might contain IBodyElements:
 * main document, headers, footers, notes, etc.
 */

public class XWPFDocumentXMLBodyHandler extends AbstractDocumentXMLBodyHandler {


    enum EditType {
        NONE,
        INSERT,
        DELETE,
        MOVE_TO,
        MOVE_FROM
    }


    private final static String BOOKMARK_START = "bookmarkStart";
    private final static String BOOKMARK_END = "bookmarkEnd";
    private final static String FOOTNOTE_REFERENCE = "footnoteReference";
    private final static String INS = "ins";
    private final static String DEL = "del";
    private final static String DEL_TEXT = "delText";
    private final static String MOVE_FROM = "moveFrom";
    private final static String MOVE_TO = "moveTo";
    private final static String ENDNOTE_REFERENCE = "endnoteReference";

    private final XWPFBodyContentsHandler bodyContentsHandler;
    //private final RelationshipsManager relationshipsManager;
    private final Map<String, String> linkedRelationships;

    private boolean inDelText = false;

    private XWPFDocumentXMLBodyHandler.EditType editType = XWPFDocumentXMLBodyHandler.EditType.NONE;


    public XWPFDocumentXMLBodyHandler(XWPFBodyContentsHandler bodyContentsHandler,
                                      Map<String, String> hyperlinks) {
        this.bodyContentsHandler = bodyContentsHandler;
        this.linkedRelationships = hyperlinks;
    }


    @Override
    public void startDocument() throws SAXException {
    }

    @Override
    public void endDocument() throws SAXException {
    }

    @Override
    public void startPrefixMapping(String prefix, String uri) throws SAXException {
    }

    @Override
    public void endPrefixMapping(String prefix) throws SAXException {
    }

    @Override
    public void startElement(String uri, String localName, String qName, Attributes atts) throws SAXException {
        //TODO: checkBox, textBox, sym, headerReference, footerReference, commentRangeEnd

        if (lastStartElementWasP && ! PPR.equals(localName)) {
            bodyContentsHandler.startParagraph(currPProperties);
        }

        lastStartElementWasP = false;

        if (uri != null && uri.equals(MC_NS)) {
            if (CHOICE.equals(localName)) {
                inACChoiceDepth++;
            } else if (FALLBACK.equals(localName)) {
                inACFallbackDepth++;
            }
        }

        if (inACChoiceDepth > 0) {
            return;
        }
        //these are sorted descending by frequency
        //in our regression corpus
        if (RPR.equals(localName)) {
            inRPr = true;
        } else if (R.equals(localName)) {
            inR = true;
        } else if (T.equals(localName)) {
            inT = true;
        } else if (TAB.equals(localName)) {
            runBuffer.append(TAB_CHAR);
        } else if (P.equals(localName)) {
            lastStartElementWasP = true;
        } else if (B.equals(localName)) { //TODO: add bCs
            if(inR && inRPr) {
                currRunProperties.setBold(true);
            }
        } else if (TC.equals(localName)) {
            bodyContentsHandler.startTableCell();
        } else if (P_STYLE.equals(localName)) {
            String styleId = atts.getValue(W_NS, "val");
            currPProperties.setStyleID(styleId);
        } else if (I.equals(localName)) { //TODO: add iCs
            //rprs don't have to be inR; ignore those that aren't
            if (inR && inRPr) {
                currRunProperties.setItalics(true);
            }
        } else if (TR.equals(localName)) {
            bodyContentsHandler.startTableRow();
        } else if (NUM_PR.equals(localName)) {
            inNumPr = true;
        } else if (ILVL.equals(localName)) {
            if (inNumPr) {
                currPProperties.setIlvl(getIntVal(atts));
            }
        } else if (NUM_ID.equals(localName)) {
            if (inNumPr) {
                currPProperties.setNumId(getIntVal(atts));
            }
        } else if(BR.equals(localName)) {
            runBuffer.append(NEWLINE);
        } else if (BOOKMARK_START.equals(localName)) {
            String name = atts.getValue(W_NS, "name");
            String id = atts.getValue(W_NS, "id");
            bodyContentsHandler.startBookmark(id, name);
        } else if (BOOKMARK_END.equals(localName)) {
            String id = atts.getValue(W_NS, "id");
            bodyContentsHandler.endBookmark(id);
        } else if (HYPERLINK.equals(localName)) {
            String hyperlinkId = atts.getValue(OFFICE_DOC_RELATIONSHIP_NS, "id");
            String hyperlink = null;
            if (hyperlinkId != null) {
                hyperlink = linkedRelationships.get(hyperlinkId);
                bodyContentsHandler.hyperlinkStart(hyperlink);
            } else {
                String anchor = atts.getValue(W_NS, "anchor");
                if (anchor != null) {
                    anchor = "#" + anchor;
                }
                bodyContentsHandler.hyperlinkStart(anchor);
            }
        } else if(TBL.equals(localName)) {
            bodyContentsHandler.startTable();
        } else if (BLIP.equals(localName)) { //check for DRAWING_NS
            picRId = atts.getValue(OFFICE_DOC_RELATIONSHIP_NS, "embed");
        } else if ("cNvPr".equals(localName)) { //check for PIC_NS?
            picDescription = atts.getValue("", "descr");
        } else if (PIC.equals(localName)) {
            inPic = true; //check for PIC_NS?
        } //TODO: add sdt, sdtPr, sdtContent goes here statistically
        else if (FOOTNOTE_REFERENCE.equals(localName)) {
            String id = atts.getValue(W_NS, "id");
            bodyContentsHandler.footnoteReference(id);
        } else if (IMAGEDATA.equals(localName)) {
            picRId = atts.getValue(OFFICE_DOC_RELATIONSHIP_NS, "id");
            picDescription = atts.getValue(O_NS, "title");
        } else if (INS.equals(localName)) {
            startEditedSection(editType.INSERT, atts);
        } else if (DEL_TEXT.equals(localName)) {
            inDelText = true;
        } else if (DEL.equals(localName)) {
            startEditedSection(editType.DELETE, atts);
        } else if (MOVE_TO.equals(localName)) {
            startEditedSection(EditType.MOVE_TO, atts);
        } else if (MOVE_FROM.equals(localName)) {
            startEditedSection(editType.MOVE_FROM, atts);
        } else if (OLE_OBJECT.equals(localName)){ //check for O_NS?
            String type = null;
            String refId = null;
            //TODO: clean this up and ...want to get ProgID?
            for (int i = 0; i < atts.getLength(); i++) {
                String attLocalName = atts.getLocalName(i);
                String attValue = atts.getValue(i);
                if (attLocalName.equals("Type")) {
                    type = attValue;
                } else if (OFFICE_DOC_RELATIONSHIP_NS.equals(atts.getURI(i)) && attLocalName.equals("id")) {
                    refId = attValue;
                }
            }
            if ("Embed".equals(type)) {
                bodyContentsHandler.embeddedOLERef(refId);
            }
        } else if(CR.equals(localName)) {
            runBuffer.append(NEWLINE);
        } else if (ENDNOTE_REFERENCE.equals(localName)) {
            String id = atts.getValue(W_NS, "id");
            bodyContentsHandler.endnoteReference(id);
        }

    }

    private void startEditedSection(EditType editType, Attributes atts) {
        String editAuthor = atts.getValue(W_NS, "author");
        String editDateString = atts.getValue(W_NS, "date");
        Date editDate = null;
        if (editDateString != null) {
            editDate = DateUtils.tryToParse(editDateString);
        }
        bodyContentsHandler.startEditedSection(editAuthor, editDate, editType);
        this.editType = editType;
    }

    private int getIntVal(Attributes atts) {
        String valString = atts.getValue(W_NS, "val");
        if (valString != null) {
            try {
                return Integer.parseInt(valString);
            } catch (NumberFormatException e) {
                //swallow
            }
        }
        return -1;
    }


    @Override
    public void endElement(String uri, String localName, String qName) throws SAXException {

        if (CHOICE.equals(localName)) {
            inACChoiceDepth--;
        } else if (FALLBACK.equals(localName)) {
            inACFallbackDepth--;
        }
        if (inACChoiceDepth > 0) {
            return;
        }

        if (PIC.equals(localName)) { //PIC_NS
            handlePict();
            inPic = false;
            return;
        } else if (RPR.equals(localName)) {
            inRPr = false;
        } else if (R.equals(localName)) {
            bodyContentsHandler.run(currRunProperties, runBuffer.toString());
            inR = false;
            runBuffer.setLength(0);
            currRunProperties.setBold(false);
            currRunProperties.setItalics(false);
        } else if (T.equals(localName)) {
            inT = false;
        } else if (PPR.equals(localName)) {
            bodyContentsHandler.startParagraph(currPProperties);
            currPProperties.reset();
        } else if (P.equals(localName)) {
            bodyContentsHandler.endParagraph();
        } else if (TC.equals(localName)) {
            bodyContentsHandler.endTableCell();
        } else if (TR.equals(localName)) {
            bodyContentsHandler.endTableRow();
        } else if (TBL.equals(localName)) {
            bodyContentsHandler.endTable();
        } else if (HYPERLINK.equals(localName)) {
            bodyContentsHandler.hyperlinkEnd();
        } else if (DEL_TEXT.equals(localName)) {
            inDelText = false;
        } else if (INS.equals(localName) || DEL.equals(localName) ||
                MOVE_TO.equals(localName) || MOVE_FROM.equals(localName)) {
            editType = EditType.NONE;
        } else if (PICT.equals(localName)) {
            handlePict();

        }
    }

    private void handlePict() {
        String picFileName = null;
        if (picRId != null) {
            picFileName = linkedRelationships.get(picRId);
        }
        bodyContentsHandler.embeddedPicRef(picFileName, picDescription);
        picDescription = null;
        picRId = null;
        inPic = false;
    }

    @Override
    public void characters(char[] ch, int start, int length) throws SAXException {

        if (inACChoiceDepth > 0) {
            return;
        }
        if (editType.equals(EditType.MOVE_FROM) && inT) {
            if (bodyContentsHandler.getIncludeMoveFromText()) {
                runBuffer.append(ch, start, length);
            }
        } else if (inT) {
            runBuffer.append(ch, start, length);
        } else if (bodyContentsHandler.getIncludeDeletedText() && editType.equals(EditType.DELETE)) {
            runBuffer.append(ch, start, length);
        }
    }

    @Override
    public void ignorableWhitespace(char[] ch, int start, int length) throws SAXException {
        if (inACChoiceDepth > 0) {
            return;
        }

        if (inT) {
            runBuffer.append(ch, start, length);
        } else if (bodyContentsHandler.getIncludeDeletedText() && inDelText) {
            runBuffer.append(ch, start, length);
        }
    }


    public interface XWPFBodyContentsHandler {

        void run(RunProperties runProperties, String contents);

        /**
         * @param link the link; can be null
         */
        void hyperlinkStart(String link);

        void hyperlinkEnd();

        void startParagraph(ParagraphProperties paragraphProperties);

        void endParagraph();

        void startTable();

        void endTable();

        void startTableRow();

        void endTableRow();

        void startTableCell();

        void endTableCell();

        void startSDT();

        void endSDT();

        void startEditedSection(String editor, Date date, EditType editType);

        void endEditedSection();

        boolean getIncludeDeletedText();

        void footnoteReference(String id);

        void endnoteReference(String id);

        boolean getIncludeMoveFromText();

        void embeddedOLERef(String refId);

        void embeddedPicRef(String picFileName, String picDescription);

        void startBookmark(String id, String name);

        void endBookmark(String id);
    }
}