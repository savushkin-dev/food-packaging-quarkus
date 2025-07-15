package org.acme.foodpackaging.sql;

public class SqlQueries {

    private SqlQueries() {

    }

    public static final String LOAD_JOBS = """
        SELECT  v.SNPZ, v.DTI, v.KMC, v.KOLMV, v.MASSA, v.KOLEV, v.NP, v.UX, m.EAN13, m.NAME
        FROM [MES].[dbo].[BD_VZPMC] AS v, NS_MC AS m
        WHERE (v.KMC = m.KMC) AND (v.DTI = ?)
        ORDER BY v.NP
        """;

}
