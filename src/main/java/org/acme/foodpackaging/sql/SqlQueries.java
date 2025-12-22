package org.acme.foodpackaging.sql;

import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;

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
        v.PDUR, v.SNPZ, v.NOTE
    FROM [MES].[dbo].[OEE_PEV] v
    WHERE
        v.SNPZ = 0
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

    public static final String LOAD_JOBS = """
    SELECT v.KSK, v.SNPZ, v.DTI, v.DTM, v.KMC, v.EMK, v.KOLMV, v.MASSA, v.KOLEV, v.NP, v.UX,
           m.MASSA, m.EAN13, m.SNM, m.NAME
    FROM [MES].[dbo].[BD_VZPMC] AS v, NS_MC AS m
    WHERE (v.KMC = m.KMC) AND (v.DTI = ?) AND (v.KSK = ?) AND (m.MASSA < ?)
    ORDER BY v.SNPZ
    """;
    
    public static final String LOAD_DLC_JOBS = """
    SELECT m.SNM, v.[KMC], v.[DTI], v.[DTF], v.[NP], v.[KOLEV], v.[UX], v.[SNPZ], v.[MASSA]
    FROM [MES].[dbo].[PLR_PDAYNP] AS v, NS_MC AS m
    WHERE (v.KMC = m.KMC) AND  (v.DTF >= ?) AND (v.DTF < ?)  AND (v.KSK = ?)
    ORDER BY v.SNPZ
    """;

    public static final String LOAD_PDAY = """
        SELECT m.SNM, v.[KMC], v.[DTI], v.[DTF], v.[NP], v.[KOLEV], v.[UX], v.[SNPZ], v.[MASSA]
        FROM [MES].[dbo].[PLR_PDAYNP] AS v, NS_MC AS m
        WHERE (v.KMC = m.KMC) AND (v.DTI >= ?) AND (v.DTI < ?) AND (v.KSK = ?)
        ORDER BY v.SNPZ
    """;

    public static final String LOAD_JOBS_FOR_SELECTED_DATE = """
       SELECT v.KSK, v.SNPZ, v.DTI, v.DTF, v.KMC, v.MASSA, v.KOLEV, v.NP, v.UX,
       m.MASSA, m.EAN13, m.SNM, m.NAME
       FROM [MES].[dbo].[PLR_PDAYNP] AS v, NS_MC AS m
       WHERE (v.KMC = m.KMC) AND (v.DTF = ? ) AND (v.KSK = ? ) AND (m.MASSA < ? )
       ORDER BY v.KMC, v.NP
    """;

    public static final String LOAD_VZPMC = """
        SELECT m.SNM, v.[KMC], v.[DTI], '' as [DTF], v.[NP], v.[KOLEV], v.[UX], v.[SNPZ], v.[MASSA]
        FROM [MES].[dbo].[BD_VZPMC] AS v, NS_MC AS m
        WHERE (v.KMC = m.KMC) AND (v.DTI >= ?) AND (v.DTI < ?) AND (v.KSK = ?) AND (m.MASSA < ?)
        ORDER BY v.SNPZ
    """;

    public static final String INSERT_PDAY  = """
       IF NOT EXISTS (SELECT 1 FROM [MES].[dbo].[PLR_PDAYNP] WHERE SNPZ = ?)
       BEGIN
         insert into [MES].[dbo].[PLR_PDAYNP] (KSK, KRC, KMC, DTI, NP, KOLEV, UX, SNPZ, MASSA) values (?, '', ?, ?, ?, ?, ?, ?, ?)
       END
    """;

    public static final String UPDATE_PDAYDTF  = """
       update [MES].[dbo].[PLR_PDAYNP] set DTF=? where SNPZ=?
    """;


    public static final String LOAD_LABELING_CACTUS_COCONUT_ALMONDS = """
    SELECT TOP (1000)
           [KMC],
           [NP],
           [DTS],
           [NKOLE],
           [MRPL],
           [DTE],
           [LINEID],
           [STP_AVT]
    FROM [prommark].[dbo].[PM_ASSCC]
    WHERE (KMC LIKE ? OR KMC LIKE ?)
      AND CAST(DTF AS DATE) = ?
    ORDER BY NP
    """;

    public static final String LOAD_LINES_SPEEDS = """
    SELECT TOP (1000)
         [KRC],
         [GRF],
         [PROD]
    FROM [mes].[dbo].[PLR_PLINES]
    WHERE PROD IS NOT NULL
    ORDER BY KRC
    """;

    public static final String LOAD_PRODUCTS = """
    SELECT p.KMC,
           p.EAN13,
           p.GRF,
           p.TGLAZ,
           p.TMASS,
           p.TFBF,
           n.SNM,
           n.KRKMC
    FROM [MES].[dbo].[PLR_MC] p
    JOIN [MES].[dbo].[NS_MC] n
        ON p.KMC = n.KMC
    WHERE p.F_DEL = 0
    """;

    public String getLoadCleaningRules() { return """
            SELECT [NPAR], [FROM_VALUE], [TO_VALUE], [DUR] FROM [MES].[dbo].[PLR_CHANGE] WHERE (F_DEL=0) AND (KRC='
            """
            + krc +
            """
                    ') ORDER BY NPAR """; }



    public static final String LOAD_LINES = """
            select krc from PLR_PLINES
                              group by krc
    """;

    public static final String LOAD_LINES_WITH_NAME = """
     SELECT KRC, SNM
     FROM [MES].[dbo].[PLR_PLINES]
             where F_DEL=0
             group by KRC, SNM
             order by SNM
    """;

    public static final String UPSERT_SOLUTION_TO_JSON = """
    MERGE dbo.PLR_PLAN AS target
    USING (VALUES (?, ?, ?)) AS src (KRC, DT, [PLAN])
        ON target.DT = src.DT
    WHEN MATCHED THEN
        UPDATE SET
            target.KRC = src.KRC,
            target.[PLAN] = src.[PLAN],
            target.F_DEL = 0
    WHEN NOT MATCHED THEN
        INSERT (KRC, DT, [PLAN])
        VALUES (src.KRC, src.DT, src.[PLAN]);
""";

    public static final String DELETE_SOLUTION_JSON  = """
   update [MES].[dbo].[PLR_PLAN]  set F_DEL=1 where DT = ?
""";

    public static final String SELECT_SOLUTION_FROM_JSON = """
        SELECT [PLAN]
        FROM dbo.PLR_PLAN
        WHERE DT = ? AND F_DEL = 0
    """;

    public static final String LOAD_LINE_WORK_FACT = """
        SELECT datetime1, project, wc, producttype, batch
        FROM reports.batchstat
        WHERE datetime1 >= ? AND datetime1 <= ?
        ORDER BY project, datetime1
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

