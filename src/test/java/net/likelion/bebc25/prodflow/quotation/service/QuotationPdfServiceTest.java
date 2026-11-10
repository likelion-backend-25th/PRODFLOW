package net.likelion.bebc25.prodflow.quotation.service;

import net.likelion.bebc25.prodflow.quotation.dto.QuotationItemDto;
import net.likelion.bebc25.prodflow.quotation.enums.QuotationStatus;
import org.junit.jupiter.api.Test;
import org.openpdf.text.PageSize;
import org.openpdf.text.pdf.PdfDictionary;
import org.openpdf.text.pdf.PdfName;
import org.openpdf.text.pdf.PdfReader;
import org.openpdf.text.pdf.parser.PdfTextExtractor;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.IntStream;

import static net.likelion.bebc25.prodflow.quotation.support.QuotationTestFixtures.draft;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

class QuotationPdfServiceTest {
    private final QuotationPdfService service = new QuotationPdfService();

    @Test
    void generatesOneA4PageWithKoreanTextAndEmbeddedRegularAndBoldFonts() throws Exception {
        byte[] pdf = service.generate(draft());
        Path previewDirectory = Files.createDirectories(Path.of("build", "pdf-preview"));
        Files.write(previewDirectory.resolve("quotation-draft.pdf"), pdf);

        try (PdfReader reader = new PdfReader(pdf)) {
            assertThat(reader.getNumberOfPages()).isEqualTo(1);
            assertThat(reader.getPageSize(1).getWidth()).isEqualTo(PageSize.A4.getWidth());
            assertThat(reader.getPageSize(1).getHeight()).isEqualTo(PageSize.A4.getHeight());
            String text = new PdfTextExtractor(reader).getTextFromPage(1);
            assertThat(text).contains("견 적 서", "테스트 거래처", "담당자", "PRODFLOW", "작성 직원",
                    "한글 품목", "기종", "110,000", "100,000", "10,000", "VAT 별도",
                    "QT-20261104-123", "2026-11-18", "희망 납기", "예상 납기", "2026-12-10", "수주 등록 시 재확인")
                    .doesNotContain("초안", "발송 전 확인용");

            PdfDictionary fonts = reader.getPageN(1).getAsDict(PdfName.RESOURCES).getAsDict(PdfName.FONT);
            assertThat(fonts.getKeys()).hasSize(2);
            for (PdfName key : fonts.getKeys()) {
                PdfDictionary font = fonts.getAsDict(key);
                PdfDictionary descendant = font.getAsArray(PdfName.DESCENDANTFONTS).getAsDict(0);
                PdfDictionary descriptor = descendant.getAsDict(PdfName.FONTDESCRIPTOR);
                assertThat(descriptor.get(PdfName.FONTFILE2)).isNotNull();
            }
        }
    }

    @Test
    void displaysExampleSupplierInformationAndCreatedByInsteadOfCurrentUserName() throws Exception {
        var draft = draft();
        when(draft.vatIncluded()).thenReturn(true);
        when(draft.userName()).thenReturn("변경된 직원 이름");
        try (PdfReader reader = new PdfReader(service.generate(draft))) {
            String text = new PdfTextExtractor(reader).getTextFromPage(1);
            assertThat(text).contains("VAT 포함", "PRODFLOW", "담당자: 작성 직원", "000-00-00000 (예시)",
                            "서울특별시 예시로 123 (예시)", "02-0000-0000 (예시)")
                    .doesNotContain("VAT 별도", "대표자:", "미설정", "변경된 직원 이름");
        }
    }

    @Test
    void rejectsMoreThanOnePageInsteadOfReturningTruncatedOrMultiplePagePdf() {
        var draft = draft();
        when(draft.items()).thenReturn(IntStream.range(0, 60)
                .mapToObj(index -> new QuotationItemDto.Response((long) index, (long) index,
                        "한글 품목 " + index, "기종", 100, 1000L, 110000L)).toList());
        assertThatThrownBy(() -> service.generate(draft))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("한 페이지");
    }

    @Test
    void cannotRegenerateAnIssuedQuotationThroughDraftPdfService() {
        var draft = draft();
        for (QuotationStatus status : QuotationStatus.values()) {
            if (status != QuotationStatus.DRAFT) {
                when(draft.status()).thenReturn(status);
                assertThatThrownBy(() -> service.generate(draft))
                        .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("초안");
            }
        }
    }
}
