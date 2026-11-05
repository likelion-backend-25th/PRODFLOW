package net.likelion.bebc25.prodflow.quotation.service;

import net.likelion.bebc25.prodflow.quotation.dto.QuotationDto;
import net.likelion.bebc25.prodflow.quotation.dto.QuotationItemDto;
import net.likelion.bebc25.prodflow.quotation.enums.QuotationStatus;
import org.openpdf.text.Document;
import org.openpdf.text.DocumentException;
import org.openpdf.text.Element;
import org.openpdf.text.Font;
import org.openpdf.text.PageSize;
import org.openpdf.text.Paragraph;
import org.openpdf.text.Phrase;
import org.openpdf.text.pdf.BaseFont;
import org.openpdf.text.pdf.PdfPCell;
import org.openpdf.text.pdf.PdfPTable;
import org.openpdf.text.pdf.PdfReader;
import org.openpdf.text.pdf.PdfWriter;
import org.springframework.core.io.ClassPathResource;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.text.NumberFormat;
import java.util.Locale;

@Service
@PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ORDER_MANAGER')")
public class QuotationPdfService {
    private static final String SUPPLIER_COMPANY = "PRODFLOW";
    private static final String SUPPLIER_BUSINESS_NUMBER = "000-00-00000 (예시)";
    private static final String SUPPLIER_ADDRESS = "서울특별시 예시로 123 (예시)";
    private static final String SUPPLIER_PHONE = "02-0000-0000 (예시)";

    // 저장된 초안 데이터로 PDF 생성
    public byte[] generate(QuotationDto.DetailResponse quotation) {
        if (quotation.status() != QuotationStatus.DRAFT) {
            throw new IllegalArgumentException("초안 상태의 견적만 PDF를 생성할 수 있습니다.");
        }
        try {
            Font regular = new Font(loadFont("NanumGothic-Regular.ttf"), 10);
            Font bold = new Font(loadFont("NanumGothic-Bold.ttf"), 10);
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            Document document = new Document(PageSize.A4, 42.5f, 42.5f, 42.5f, 42.5f);
            try {
                PdfWriter.getInstance(document, output);
                document.addTitle("견적서 " + quotation.quotationNo());
                document.addCreator("PRODFLOW");
                document.open();

                Paragraph title = new Paragraph("견 적 서", new Font(bold.getBaseFont(), 22));
                title.setAlignment(Element.ALIGN_CENTER);
                title.setSpacingAfter(14);
                document.add(title);
                document.add(new Paragraph("견적번호: " + quotation.quotationNo(), bold));
                document.add(new Paragraph("발행일: " + quotation.createdAt().toLocalDate()
                        + "    유효기간: " + quotation.validUntil(), regular));

                PdfPTable parties = table(new float[]{1, 1});
                parties.addCell(cell("수신 거래처\n" + quotation.clientCompany()
                        + "\n담당자: " + quotation.clientName()
                        + "\n연락처: " + quotation.clientPhone()
                        + "\n이메일: " + quotation.clientEmail(), regular, Element.ALIGN_LEFT));
                parties.addCell(cell("공급자\n" + SUPPLIER_COMPANY
                        + "\n담당자: " + quotation.createdBy()
                        + "\n사업자번호: " + SUPPLIER_BUSINESS_NUMBER
                        + "\n주소: " + SUPPLIER_ADDRESS
                        + "\n연락처: " + SUPPLIER_PHONE, regular, Element.ALIGN_LEFT));
                document.add(parties);

                Paragraph total = new Paragraph("견적 총액  " + amount(quotation.totalAmount())
                        + "원 (VAT 포함)", new Font(bold.getBaseFont(), 14));
                total.setSpacingBefore(8);
                total.setSpacingAfter(8);
                document.add(total);

                PdfPTable items = table(new float[]{0.5f, 2.2f, 1.4f, 0.9f, 1.5f, 1.7f});
                String priceTitle = quotation.vatIncluded() ? "단가\n(VAT 포함)" : "단가\n(VAT 별도)";
                for (String heading : new String[]{"번호", "품목명", "기종", "수량", priceTitle, "금액\n(VAT 포함)"}) {
                    PdfPCell header = cell(heading, bold, Element.ALIGN_CENTER);
                    header.setBackgroundColor(new Color(240, 242, 245));
                    items.addCell(header);
                }
                int index = 1;
                for (QuotationItemDto.Response item : quotation.items()) {
                    items.addCell(cell(String.valueOf(index++), regular, Element.ALIGN_CENTER));
                    items.addCell(cell(item.productName(), regular, Element.ALIGN_LEFT));
                    items.addCell(cell(item.deviceName(), regular, Element.ALIGN_LEFT));
                    items.addCell(cell(amount(item.quantity()), regular, Element.ALIGN_RIGHT));
                    items.addCell(cell(amount(item.unitPrice()), regular, Element.ALIGN_RIGHT));
                    items.addCell(cell(amount(item.lineAmount()), regular, Element.ALIGN_RIGHT));
                }
                document.add(items);

                PdfPTable summary = table(new float[]{1, 1});
                addSummary(summary, "공급가액", quotation.supplyAmount(), regular);
                addSummary(summary, "부가세", quotation.vatAmount(), regular);
                addSummary(summary, "총액", quotation.totalAmount(), bold);
                document.add(summary);
                PdfPTable dueDates = table(new float[]{1, 1.6f, 1, 1.6f});
                dueDates.setKeepTogether(true);
                addDueDate(dueDates, "희망 납기", quotation.requestedDueDate().toString(), bold);
                addDueDate(dueDates, "예상 납기", quotation.estimatedDueDate().toString(), bold);
                document.add(dueDates);
                document.add(new Paragraph("예상 납기는 견적 시점 기준이며, 수주 등록 시 재확인됩니다.",
                        new Font(regular.getBaseFont(), 9, Font.NORMAL, new Color(95, 100, 110))));
            } finally {
                document.close();
            }
            byte[] pdf = output.toByteArray();
            try (PdfReader reader = new PdfReader(pdf)) {
                if (reader.getNumberOfPages() != 1) {
                    throw new IllegalArgumentException("견적 내용이 A4 한 페이지를 초과합니다. 품목 수나 내용 길이를 줄여주세요.");
                }
            }
            return pdf;
        } catch (IOException ex) {
            throw new UncheckedIOException("견적 PDF의 폰트 또는 파일을 처리하지 못했습니다.", ex);
        } catch (DocumentException ex) {
            throw new RuntimeException("견적 PDF를 생성하지 못했습니다.", ex);
        }
    }

    private BaseFont loadFont(String filename) throws IOException {
        try (var input = new ClassPathResource("fonts/" + filename).getInputStream()) {
            return BaseFont.createFont(filename, BaseFont.IDENTITY_H, BaseFont.EMBEDDED,
                    false, input.readAllBytes(), null);
        }
    }

    private PdfPTable table(float[] widths) {
        PdfPTable table = new PdfPTable(widths);
        table.setWidthPercentage(100);
        table.setSpacingBefore(10);
        table.setSpacingAfter(10);
        return table;
    }

    private PdfPCell cell(String text, Font font, int alignment) {
        PdfPCell cell = new PdfPCell(new Phrase(text, font));
        cell.setPadding(7);
        cell.setLeading(3, 1.2f);
        cell.setHorizontalAlignment(alignment);
        cell.setVerticalAlignment(Element.ALIGN_MIDDLE);
        cell.setBorderColor(new Color(210, 215, 220));
        return cell;
    }

    private void addDueDate(PdfPTable table, String label, String date, Font font) {
        PdfPCell heading = cell(label, font, Element.ALIGN_CENTER);
        heading.setBackgroundColor(new Color(240, 240, 240));
        heading.setPaddingTop(12);
        heading.setPaddingBottom(12);
        table.addCell(heading);
        table.addCell(cell(date, new Font(font.getBaseFont(), 13, Font.NORMAL, Color.BLACK), Element.ALIGN_CENTER));
    }

    private void addSummary(PdfPTable table, String label, long value, Font font) {
        table.addCell(cell(label, font, Element.ALIGN_LEFT));
        table.addCell(cell(amount(value) + "원", font, Element.ALIGN_RIGHT));
    }

    private String amount(long value) {
        return NumberFormat.getIntegerInstance(Locale.KOREA).format(value);
    }

}
