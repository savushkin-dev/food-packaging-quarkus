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
}
