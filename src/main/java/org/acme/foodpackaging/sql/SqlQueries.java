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
}
