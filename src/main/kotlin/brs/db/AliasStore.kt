package brs.db

import brs.entity.Alias

interface AliasStore {
    /**
     * TODO
     */
    val aliasDbKeyFactory: BurstKey.LongKeyFactory<Alias>

    /**
     * TODO
     */
    val offerDbKeyFactory: BurstKey.LongKeyFactory<Alias.Offer>

    /**
     * TODO
     */
    val aliasTable: VersionedEntityTable<Alias>

    /**
     * TODO
     */
    val offerTable: VersionedEntityTable<Alias.Offer>

    /**
     * TODO
     */
    fun getAliasesByOwner(accountId: Long, from: Int, to: Int): Collection<Alias>

    /**
     * TODO
     */
    fun getAlias(aliasName: String): Alias?
}
