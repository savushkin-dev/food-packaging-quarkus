package service.materials;

import org.acme.foodpackaging.dto.materials.OneCReqGroupDto;
import org.acme.foodpackaging.entity.materials.Plr1cReq;
import org.acme.foodpackaging.entity.materials.PlrMt;
import org.acme.foodpackaging.repository.materials.OneCReqRepository;
import org.acme.foodpackaging.service.materials.OneCLogService;
import org.acme.foodpackaging.service.materials.config.MtService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Month;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OneCLogServiceTest {

    @InjectMocks
    private OneCLogService oneCLogService;

    @Mock
    private OneCReqRepository oneCReqRepository;

    @Mock
    private MtService mtService;

    private final LocalDate testDate = LocalDate.of(2026, Month.FEBRUARY, 15);
    private final String testDateStr = "2026-02-15";
    private final String testKpp = "01020391";
    private final String testType = "M";

    // ==================== getLog() ====================

    @Test
    void testGetLog_Success_GroupsByReq1c() {
        // Arrange
        List<Plr1cReq> records = List.of(
                createReq("REQ-1", "1002051408", 10.0, "2026-02-15T10:00:00"),
                createReq("REQ-1", "1002110286", 20.0, "2026-02-15T10:00:00"),
                createReq("REQ-2", "1002051408", 30.0, "2026-02-15T11:00:00")
        );

        when(oneCReqRepository.findByDtAndKpp2AndType(testDate, testKpp, testType))
                .thenReturn(records);
        when(mtService.getByKmt(anyString())).thenReturn(createMt());

        // Act
        List<OneCReqGroupDto> result = oneCLogService.getLog(testDateStr, testKpp, testType);

        // Assert
        assertNotNull(result);
        assertEquals(2, result.size());

        OneCReqGroupDto first = result.get(0);
        assertEquals("REQ-1", first.getReq1c());
        assertEquals(2, first.getMaterials().size());
        assertEquals("01020391", first.getKpp1());
        assertEquals(testKpp, first.getKpp2());
        assertEquals(testType, first.getType());
        assertEquals("user-1", first.getUserId());
        assertEquals("127.0.0.1", first.getIp());
        assertNotNull(first.getSentAt());

        OneCReqGroupDto second = result.get(1);
        assertEquals("REQ-2", second.getReq1c());
        assertEquals(1, second.getMaterials().size());

        verify(oneCReqRepository, times(1))
                .findByDtAndKpp2AndType(testDate, testKpp, testType);
    }

    @Test
    void testGetLog_EmptyRecords_ReturnsEmptyList() {
        // Arrange
        when(oneCReqRepository.findByDtAndKpp2AndType(testDate, testKpp, testType))
                .thenReturn(Collections.emptyList());

        // Act
        List<OneCReqGroupDto> result = oneCLogService.getLog(testDateStr, testKpp, testType);

        // Assert
        assertNotNull(result);
        assertTrue(result.isEmpty());
        verifyNoInteractions(mtService);
    }

    @Test
    void testGetLog_FiltersOutNullAndEmptyReq1c() {
        // Arrange
        Plr1cReq nullReq = createReq(null, "1002051408", 10.0, "2026-02-15T10:00:00");
        Plr1cReq emptyReq = createReq("", "1002051408", 10.0, "2026-02-15T10:00:00");
        Plr1cReq validReq = createReq("REQ-1", "1002051408", 10.0, "2026-02-15T10:00:00");

        when(oneCReqRepository.findByDtAndKpp2AndType(testDate, testKpp, testType))
                .thenReturn(List.of(nullReq, emptyReq, validReq));
        when(mtService.getByKmt(anyString())).thenReturn(createMt());

        // Act
        List<OneCReqGroupDto> result = oneCLogService.getLog(testDateStr, testKpp, testType);

        // Assert
        assertEquals(1, result.size());
        assertEquals("REQ-1", result.get(0).getReq1c());
    }

    @Test
    void testGetLog_TrimsKppFields() {
        // Arrange
        Plr1cReq req = createReq("REQ-1", "1002051408", 10.0, "2026-02-15T10:00:00");
        req.setKpp1("  01020391  ");
        req.setKpp2("  01020392  ");

        when(oneCReqRepository.findByDtAndKpp2AndType(testDate, testKpp, testType))
                .thenReturn(List.of(req));
        when(mtService.getByKmt(anyString())).thenReturn(createMt());

        // Act
        List<OneCReqGroupDto> result = oneCLogService.getLog(testDateStr, testKpp, testType);

        // Assert
        assertEquals("01020391", result.get(0).getKpp1());
        assertEquals("01020392", result.get(0).getKpp2());
    }

    @Test
    void testGetLog_MapsMaterialFields() {
        // Arrange
        Plr1cReq req = createReq("REQ-1", "1002051408", 42.5, "2026-02-15T10:00:00");

        PlrMt mt = new PlrMt();
        mt.setKmt("1002051408");
        mt.setSnm("Тестовый материал");
        mt.setEdu("кг");

        when(oneCReqRepository.findByDtAndKpp2AndType(testDate, testKpp, testType))
                .thenReturn(List.of(req));
        when(mtService.getByKmt("1002051408")).thenReturn(mt);

        // Act
        List<OneCReqGroupDto> result = oneCLogService.getLog(testDateStr, testKpp, testType);

        // Assert
        OneCReqGroupDto.OneCReqItemDto item = result.get(0).getMaterials().get(0);
        assertEquals("1002051408", item.getKmt());
        assertEquals("Тестовый материал", item.getSnmMt());
        assertEquals("кг", item.getEduMt());
        assertEquals(42.5, item.getKole());
    }

    @Test
    void testGetLog_PreservesInsertionOrder() {
        // Arrange — REQ-2 пришёл раньше REQ-1, порядок должен сохраниться
        List<Plr1cReq> records = List.of(
                createReq("REQ-2", "1002051408", 10.0, "2026-02-15T10:00:00"),
                createReq("REQ-1", "1002051408", 20.0, "2026-02-15T10:00:00"),
                createReq("REQ-3", "1002051408", 30.0, "2026-02-15T10:00:00")
        );

        when(oneCReqRepository.findByDtAndKpp2AndType(testDate, testKpp, testType))
                .thenReturn(records);
        when(mtService.getByKmt(anyString())).thenReturn(createMt());

        // Act
        List<OneCReqGroupDto> result = oneCLogService.getLog(testDateStr, testKpp, testType);

        // Assert
        assertEquals(List.of("REQ-2", "REQ-1", "REQ-3"),
                result.stream().map(OneCReqGroupDto::getReq1c).toList());
    }

    @Test
    void testGetLog_InvalidDate_ThrowsException() {
        assertThrows(Exception.class,
                () -> oneCLogService.getLog("invalid-date", testKpp, testType));

        verifyNoInteractions(oneCReqRepository);
    }

    @Test
    void testGetLog_MtIsNull_ThrowsNullPointerException() {
        // Arrange — на текущей реализации mtService.getByKmt(...).getSnm() даст NPE
        Plr1cReq req = createReq("REQ-1", "1002051408", 10.0, "2026-02-15T10:00:00");

        when(oneCReqRepository.findByDtAndKpp2AndType(testDate, testKpp, testType))
                .thenReturn(List.of(req));
        when(mtService.getByKmt(anyString())).thenReturn(null);

        // Act & Assert
        assertThrows(NullPointerException.class,
                () -> oneCLogService.getLog(testDateStr, testKpp, testType));
    }

    @Test
    void testGetLog_UsesFirstRecordFieldsForGroup() {
        // Arrange — два req1c=REQ-1 с разными kpp1, должен взяться первый
        Plr1cReq first = createReq("REQ-1", "1002051408", 10.0, "2026-02-15T10:00:00");
        first.setKpp1("11111111");
        first.setUserId("user-first");

        Plr1cReq second = createReq("REQ-1", "1002110286", 20.0, "2026-02-15T12:00:00");
        second.setKpp1("22222222");
        second.setUserId("user-second");

        when(oneCReqRepository.findByDtAndKpp2AndType(testDate, testKpp, testType))
                .thenReturn(List.of(first, second));
        when(mtService.getByKmt(anyString())).thenReturn(createMt());

        // Act
        List<OneCReqGroupDto> result = oneCLogService.getLog(testDateStr, testKpp, testType);

        // Assert
        assertEquals(1, result.size());
        assertEquals("11111111", result.get(0).getKpp1());
        assertEquals("user-first", result.get(0).getUserId());
    }

    // ==================== ВСПОМОГАТЕЛЬНЫЕ МЕТОДЫ ====================

    private Plr1cReq createReq(String req1c, String kmt, Double kole, String sentAtIso) {
        return Plr1cReq.builder()
                .dt(testDate)
                .kpp1("  01020391  ")
                .kpp2(testKpp)
                .type(testType)
                .kmt(kmt)
                .kole(kole)
                .req1c(req1c)
                .userId("user-1")
                .ip("127.0.0.1")
                .sentAt(LocalDateTime.parse(sentAtIso))
                .build();
    }

    private PlrMt createMt() {
        PlrMt mt = new PlrMt();
        mt.setKmt("1002051408");
        mt.setSnm("Тестовый материал");
        mt.setEdu("кг");
        mt.setPers(10.0);
        mt.setRnd(5.0);
        return mt;
    }
}