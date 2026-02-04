package org.acme.foodpackaging.sql;

import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * SQL queries for database operations.
 * 
 * NOTE: Schema names (e.g., "MES") are environment-specific and may vary
 * between different database instances. These queries use parameterized
 * statements to prevent SQL injection and do not expose sensitive data.
 */
@ApplicationScoped
public class SqlQueries {

    @ConfigProperty(name = "krc")
    String krc;

    public SqlQueries() {

    }

    public static final String LOAD_JOBS_DB =  """
 SELECT
 v.DTI, v.KMC, v.NP, v.KOLEV, v.MASSA, v.PDTN,
 v.PDTO, v.PDUR, v.SNPZ, v.UX, v.KRC, m.SNM
 FROM [MES].[dbo].[BD_VZPMC] AS v
    JOIN [MES].[dbo].[NS_MC] AS m
      ON v.KMC = m.KMC
WHERE
   v.DTI >= CAST(?1 AS datetime)
   AND v.DTI <  CAST(?2 AS datetime)
   AND v.KSK = ?3
   AND v.F_DEL = 0
   AND v.NP > 0
   AND m.massa<0.1
   AND (
     v.PDTN IS NULL
     OR v.PDTN >= CAST(?4 AS datetime)
     )
   ORDER BY
     v.KRC, v.PDTN
""";
    public static final String LOAD_MAINTENANCE_DB = """
    SELECT
        v.F_ID, v.KRC,
        v.PDTN, v.PDTO,
        v.PDUR, v.SNPZ, v.F_DEL, v.EVTYPE, v.NOTE
    FROM [MES].[dbo].[OEE_PEV] v
    WHERE
        (v.SNPZ = 0  OR v.SNPZ IS NULL)
        AND v.F_DEL = 0
        AND (
            (
                v.PDTN >= ?1
                AND v.PDTN < ?2
            )
            OR
            (
                v.PDTO >= ?3
                AND v.PDTO < ?4
            )
        )
    ORDER BY
        v.KRC,
        v.PDTN
""";

    public static final String LOAD_FACT_DB = """
    SELECT
       v.IDBATCH, v.DTV, v.KMC, v.NP, v.KRC, v.DT, v.EVENT
    FROM [MES].[dbo].[MS_LOG] v
    WHERE
        v.DTV > ?1
        AND v.DTV <= ?2
        AND v.EVENT <=3
    ORDER BY
        v.DTV, v.KMC, v.NP
""";


public static final String LOAD_CAMERA_FACT = """
 SELECT
  MIN(DTS) AS DTSTART,
  MAX(DTS) AS DTEND
  FROM [prommark].[dbo].[PM_LOG] WITH (NOLOCK)
  WHERE IDBATCH = ? AND KD = 71
""";

    public static final String UPDATE_CAMERA_END_EVENT = """
    UPDATE [MES].[dbo].[MS_LOG]
    SET DT = ?
    WHERE IDBATCH = ?
      AND EVENT = ?
""";

public static final String INSERT_CAMERA_EVENT = """
    INSERT INTO [MES].[dbo].[MS_LOG] (IDBATCH, KMC, KRC, NP, EVENT, DTV, DT)
    VALUES (?, ?, ?, ?, ?, ?, ?)
""";

public static final String UPDATE_WORK = """
    update [MES].[dbo].[BD_VZPMC]
    set KRC=?, PDTN=?, PDTO=?, PDUR=?
    where SNPZ=?;
    """;

public static final String REFRESH_FASP = "DECLARE @pdt1 datetime = GETDATE()-2, " +
"        @pdt2 datetime = GETDATE()+7, " +
"        @pkrca char(20) = ?, " +
"        @pksk  char(10) = ? " +
"EXEC mes_refreshfasp @pdt1, @pdt2, @pkrca, 1, 14, @pksk ";
}
