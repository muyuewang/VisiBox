package com.visibox.tv.db.dao;

import androidx.room.Dao;
import androidx.room.Query;

import com.visibox.tv.bean.SiteHealth;

import java.util.List;

@Dao
public abstract class SiteHealthDao extends BaseDao<SiteHealth> {

    @Query("SELECT * FROM SiteHealth")
    public abstract List<SiteHealth> findAll();

    @Query("SELECT * FROM SiteHealth WHERE `key` = :key LIMIT 1")
    public abstract SiteHealth find(String key);

    @Query("DELETE FROM SiteHealth WHERE `key` = :key")
    public abstract void delete(String key);

    @Query("DELETE FROM SiteHealth")
    public abstract void deleteAll();
}
