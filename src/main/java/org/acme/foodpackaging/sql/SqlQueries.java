package org.acme.foodpackaging.sql;

public class SqlQueries {

    private SqlQueries() {

    }

    public static final String LOAD_JOBS = """
    SELECT v.KSK, v.SNPZ, v.DTI, v.DTM, v.KMC, v.EMK, v.KOLMV, v.MASSA, v.KOLEV, v.NP, v.UX,
           m.MASSA, m.EAN13, m.SNM, m.NAME
    FROM [MES].[dbo].[BD_VZPMC] AS v, NS_MC AS m
    WHERE (v.KMC = m.KMC) AND (v.DTI = ?) AND (v.KSK = ?) AND (m.MASSA < ?)
    ORDER BY v.SNPZ
    """;

    public static final String LOAD_PDAY = """
        SELECT m.SNM, v.[KMC], v.[DTI], v.[DTF], v.[NP], v.[KOLEV], v.[UX], v.[SNPZ], v.[MASSA]
        FROM [MES].[dbo].[PLR_PDAYNP] AS v, NS_MC AS m
        WHERE (v.KMC = m.KMC) AND (v.DTI >= ?) AND (v.DTI < ?) AND (v.KSK = ?)
        ORDER BY v.SNPZ
    """;

    public static final String LOAD_VZPMC = """
        SELECT m.SNM, v.[KMC], v.[DTI], '' as [DTF], v.[NP], v.[KOLEV], v.[UX], v.[SNPZ], v.[MASSA]
        FROM [MES].[dbo].[BD_VZPMC] AS v, NS_MC AS m
        WHERE (v.KMC = m.KMC) AND (v.DTI >= ?) AND (v.DTI < ?) AND (v.KSK = ?) AND (m.MASSA < ?)
        ORDER BY v.SNPZ
    """;

    public static final String INSERT_PDAY  = """
       insert into [MES].[dbo].[PLR_PDAYNP] (KSK, KRC, KMC, DTI, NP, KOLEV, UX, SNPZ, MASSA) values (?, '', ?, ?, ?, ?, ?, ?, ?)
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

    public static final String LOAD_CLEANING_RULES = """
    SELECT [NPAR], [FROM_VALUE], [TO_VALUE], [DUR]
      FROM [MES].[dbo].[PLR_CHANGE]
       where (F_DEL=0) and (KRC='170610000000')
       order by NPAR
    """;

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
}

