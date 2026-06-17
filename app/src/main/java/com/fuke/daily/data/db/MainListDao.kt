package com.fuke.daily.data.db

import androidx.room.*
import com.fuke.daily.data.model.*
import kotlinx.coroutines.flow.Flow

// ═══════════════════════════════════════════════════
//  主列表 DAO
// ═══════════════════════════════════════════════════

@Dao
interface MainListDao {

    // ── 主列表 ──

    @Query("SELECT * FROM main_lists ORDER BY sortOrder ASC, createdAt ASC")
    fun getAllLists(): Flow<List<MainList>>

    @Query("SELECT * FROM main_lists WHERE type = :type ORDER BY sortOrder ASC")
    fun getListsByType(type: ListType): Flow<List<MainList>>

    @Query("SELECT * FROM main_lists WHERE id = :id")
    suspend fun getListById(id: Long): MainList?

    @Query("SELECT * FROM main_lists WHERE type = 'MAINLINE' LIMIT 1")
    suspend fun getMainlineList(): MainList?

    @Insert
    suspend fun insertList(list: MainList): Long

    @Update
    suspend fun updateList(list: MainList)

    @Delete
    suspend fun deleteList(list: MainList)

    @Query("DELETE FROM main_lists WHERE id = :id")
    suspend fun deleteListById(id: Long)

    // ── 子列表 ──

    @Query("SELECT * FROM sub_lists WHERE parentListId = :parentId ORDER BY sortOrder ASC")
    fun getSubLists(parentId: Long): Flow<List<SubList>>

    @Query("SELECT * FROM sub_lists WHERE parentListId = :parentId ORDER BY sortOrder ASC")
    suspend fun getSubListsOnce(parentId: Long): List<SubList>

    @Insert
    suspend fun insertSubList(subList: SubList): Long

    @Update
    suspend fun updateSubList(subList: SubList)

    @Delete
    suspend fun deleteSubList(subList: SubList)

    @Query("DELETE FROM sub_lists WHERE parentListId = :parentId")
    suspend fun deleteSubListsByParent(parentId: Long)

    // ── 内容配置 ──

    @Query("SELECT * FROM content_configs WHERE subListId = :subListId AND parentListId = :parentId LIMIT 1")
    suspend fun getContentConfig(subListId: Long, parentId: Long): ContentConfig?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertContentConfig(config: ContentConfig)

    @Delete
    suspend fun deleteContentConfig(config: ContentConfig)

    @Query("DELETE FROM content_configs WHERE parentListId = :parentId")
    suspend fun deleteContentConfigsByParent(parentId: Long)

    // ── 选项按钮 ──

    @Query("SELECT * FROM option_buttons WHERE subListId = :subListId AND parentListId = :parentId ORDER BY sortOrder ASC")
    suspend fun getOptionButtons(subListId: Long, parentId: Long): List<OptionButton>

    @Insert
    suspend fun insertOptionButton(button: OptionButton): Long

    @Update
    suspend fun updateOptionButton(button: OptionButton)

    @Delete
    suspend fun deleteOptionButton(button: OptionButton)

    @Query("DELETE FROM option_buttons WHERE parentListId = :parentId")
    suspend fun deleteOptionButtonsByParent(parentId: Long)

    // ── 富文本 ──

    @Query("SELECT * FROM rich_texts WHERE parentListId = :parentId ORDER BY sortOrder ASC")
    fun getRichTexts(parentId: Long): Flow<List<RichText>>

    @Query("SELECT * FROM rich_texts WHERE parentListId = :parentId ORDER BY sortOrder ASC")
    suspend fun getRichTextsOnce(parentId: Long): List<RichText>

    @Insert
    suspend fun insertRichText(richText: RichText): Long

    @Update
    suspend fun updateRichText(richText: RichText)

    @Delete
    suspend fun deleteRichText(richText: RichText)

    @Query("DELETE FROM rich_texts WHERE parentListId = :parentId")
    suspend fun deleteRichTextsByParent(parentId: Long)

    // ── 主线支线 ──

    @Query("SELECT * FROM mainline_branches WHERE parentListId = :parentId ORDER BY sortOrder ASC")
    suspend fun getBranches(parentId: Long): List<MainlineBranch>

    @Insert
    suspend fun insertBranch(branch: MainlineBranch): Long

    @Update
    suspend fun updateBranch(branch: MainlineBranch)

    @Delete
    suspend fun deleteBranch(branch: MainlineBranch)

    @Query("DELETE FROM mainline_branches WHERE parentListId = :parentId")
    suspend fun deleteBranchesByParent(parentId: Long)

    // ── 主线子项 ──

    @Query("SELECT * FROM mainline_items WHERE branchId = :branchId ORDER BY sortOrder ASC")
    suspend fun getBranchItems(branchId: Long): List<MainlineItem>

    @Insert
    suspend fun insertBranchItem(item: MainlineItem): Long

    @Update
    suspend fun updateBranchItem(item: MainlineItem)

    @Delete
    suspend fun deleteBranchItem(item: MainlineItem)

    @Query("DELETE FROM mainline_items WHERE branchId = :branchId")
    suspend fun deleteBranchItemsByBranch(branchId: Long)

    // ── 答题组 ──

    @Query("SELECT * FROM quiz_groups WHERE parentListId = :parentId ORDER BY sortOrder ASC")
    fun getQuizGroups(parentId: Long): Flow<List<QuizGroup>>

    @Query("SELECT * FROM quiz_groups WHERE parentListId = :parentId ORDER BY sortOrder ASC")
    suspend fun getQuizGroupsOnce(parentId: Long): List<QuizGroup>

    @Insert
    suspend fun insertQuizGroup(group: QuizGroup): Long

    @Update
    suspend fun updateQuizGroup(group: QuizGroup)

    @Delete
    suspend fun deleteQuizGroup(group: QuizGroup)

    @Query("DELETE FROM quiz_groups WHERE parentListId = :parentId")
    suspend fun deleteQuizGroupsByParent(parentId: Long)

    // ── 答题卡片 ──

    @Query("SELECT * FROM quiz_cards WHERE groupId = :groupId ORDER BY sortOrder ASC")
    suspend fun getQuizCards(groupId: Long): List<QuizCard>

    @Insert
    suspend fun insertQuizCard(card: QuizCard): Long

    @Update
    suspend fun updateQuizCard(card: QuizCard)

    @Delete
    suspend fun deleteQuizCard(card: QuizCard)

    @Query("DELETE FROM quiz_cards WHERE groupId = :groupId")
    suspend fun deleteQuizCardsByGroup(groupId: Long)
}
