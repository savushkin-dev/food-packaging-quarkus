package service.jobs;

import org.acme.foodpackaging.domain.Job;
import org.acme.foodpackaging.domain.PackagingSchedule;
import org.acme.foodpackaging.domain.Product;
import org.acme.foodpackaging.record.CameraFactRow;
import org.acme.foodpackaging.record.DbJobRow;
import org.acme.foodpackaging.repository.PmLogRepository;
import org.acme.foodpackaging.service.jobs.JobInfoService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import org.acme.foodpackaging.scheduleoperations.utils.ScheduleUtils;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class JobInfoServiceTest {

    @InjectMocks
    JobInfoService jobInfoService;

    @Mock
    PmLogRepository pmLogRepository;

    private PackagingSchedule schedule;
    private Job job;
    private Product product;
    private static final long SNPZ = 12345L;
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 2, 12, 10, 0);
    private static final int EMK = 12;

    @BeforeEach
    void setUp() {
        product = new Product();
        product.setEan13("4810268053150");
        product.setMass(2.5);

        Timestamp timestamp = Timestamp.valueOf(NOW);
        DbJobRow dbJobRow = new DbJobRow(
                timestamp, "KMC001", 111, 100, 2.5,
                timestamp, timestamp, 60, SNPZ, 1,
                "L1", "Product Name", 19, 100, false
        );

        job = Job.fromDbJobRow(dbJobRow, product, NOW, ScheduleUtils::nameCleaner);
        job.setEmk(EMK);
        job.setDti(NOW);
        schedule = new PackagingSchedule();
        schedule.setAllJobsById(new HashMap<>());
        schedule.getAllJobsById().put(SNPZ, job);
    }

    @Test
    void findFactPlace_shouldCalculateAndSetPlaceFactInfo() {
        String idBatch = jobInfoService.generateIdBatch(schedule, SNPZ);
        long countBoxes = 5L;
        when(pmLogRepository.countByIdBatch(idBatch)).thenReturn(countBoxes);

        int emk = job.getEmk();
        double mass = product.getMass();

        int expectedCountPieces = (int) (countBoxes * emk);
        int expectedBatchWeight = (int) (expectedCountPieces * mass);
        String expectedResult = String.format("%d (%d шт., %d кг.)",
                countBoxes, expectedCountPieces, expectedBatchWeight);

        PackagingSchedule result = jobInfoService.findFactPlace(schedule, SNPZ);

        assertNotNull(result);
        assertEquals(expectedResult, result.getAllJobsById().get(SNPZ).getPlaceFactInfo());
        verify(pmLogRepository).countByIdBatch(idBatch);
    }

    @Test
    void findCameraFact_shouldSetCameraStartAndEnd() {
        String idBatch = jobInfoService.generateIdBatch(schedule, SNPZ);

        LocalDateTime start = NOW.plusHours(1);
        LocalDateTime end = NOW.plusHours(2);
        CameraFactRow cameraFact = new CameraFactRow(
                Timestamp.valueOf(start),
                Timestamp.valueOf(end)
        );

        when(pmLogRepository.getCameraFactRow(idBatch)).thenReturn(cameraFact);

        PackagingSchedule result = jobInfoService.findCameraFact(schedule, SNPZ);

        assertEquals(start, result.getAllJobsById().get(SNPZ).getCameraStart());
        assertEquals(end, result.getAllJobsById().get(SNPZ).getCameraEnd());
        verify(pmLogRepository).getCameraFactRow(idBatch);
    }

    @Test
    void generateIdBatch_shouldGenerateCorrectFormat() {
        String result = jobInfoService.generateIdBatch(schedule, SNPZ);

        String expectedEan13 = product.getEan13().substring(0, 12) + "0";
        String expectedDate = NOW.format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String expectedNp = "000000111";

        assertEquals(expectedEan13 + expectedDate + expectedNp, result);
        assertEquals(30, result.length());
    }

    @Test
    void findCameraFact_withNullTimestamps_shouldSetNull() {
        String idBatch = jobInfoService.generateIdBatch(schedule, SNPZ);
        when(pmLogRepository.getCameraFactRow(idBatch)).thenReturn(new CameraFactRow(null, null));

        jobInfoService.findCameraFact(schedule, SNPZ);

        assertNull(schedule.getAllJobsById().get(SNPZ).getCameraStart());
        assertNull(schedule.getAllJobsById().get(SNPZ).getCameraEnd());
    }

    @Test
    void findFactPlace_withZeroBoxes_shouldReturnZero() {
        String idBatch = jobInfoService.generateIdBatch(schedule, SNPZ);
        when(pmLogRepository.countByIdBatch(idBatch)).thenReturn(0L);

        jobInfoService.findFactPlace(schedule, SNPZ);

        assertEquals("0 (0 шт., 0 кг.)", schedule.getAllJobsById().get(SNPZ).getPlaceFactInfo());
    }

    @Test
    void generateIdBatch_withDifferentNp_shouldPadWithZeros() {
        job.setNp(5);
        String result = jobInfoService.generateIdBatch(schedule, SNPZ);
        assertTrue(result.endsWith("000000005"));

        job.setNp(12345);
        result = jobInfoService.generateIdBatch(schedule, SNPZ);
        assertTrue(result.endsWith("000012345"));
    }
}