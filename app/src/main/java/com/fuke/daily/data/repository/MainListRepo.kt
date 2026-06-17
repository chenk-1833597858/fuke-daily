package com.fuke.daily.data.repository

import com.fuke.daily.data.db.MainListDao
import com.fuke.daily.data.model.*
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

// ═══════════════════════════════════════════════════
//  主列表仓库
// ═══════════════════════════════════════════════════

@Singleton
class MainListRepo @Inject constructor(
    private val dao: MainListDao,
) {
    // ── 主列表 ──

    fun getAllLists(): Flow<List<MainList>> = dao.getAllLists()

    fun getListsByType(type: ListType): Flow<List<MainList>> = dao.getListsByType(type)

    suspend fun getListById(id: Long): MainList? = dao.getListById(id)

    suspend fun getMainlineList(): MainList? = dao.getMainlineList()

    suspend fun insertList(list: MainList): Long = dao.insertList(list)

    suspend fun updateList(list: MainList) = dao.updateList(list)

    suspend fun deleteListById(id: Long) = dao.deleteListById(id)

    // ── 子列表 ──

    fun getSubLists(parentId: Long): Flow<List<SubList>> = dao.getSubLists(parentId)

    suspend fun getSubListsOnce(parentId: Long): List<SubList> = dao.getSubListsOnce(parentId)

    suspend fun insertSubList(subList: SubList): Long = dao.insertSubList(subList)

    suspend fun updateSubList(subList: SubList) = dao.updateSubList(subList)

    suspend fun deleteSubList(subList: SubList) = dao.deleteSubList(subList)

    // ── 内容配置 ──

    suspend fun getContentConfig(subListId: Long, parentId: Long): ContentConfig? =
        dao.getContentConfig(subListId, parentId)

    suspend fun saveContentConfig(config: ContentConfig) =
        dao.insertContentConfig(config)

    // ── 选项按钮 ──

    suspend fun getOptionButtons(subListId: Long, parentId: Long): List<OptionButton> =
        dao.getOptionButtons(subListId, parentId)

    suspend fun insertOptionButton(button: OptionButton): Long = dao.insertOptionButton(button)

    suspend fun updateOptionButton(button: OptionButton) = dao.updateOptionButton(button)

    suspend fun deleteOptionButton(button: OptionButton) = dao.deleteOptionButton(button)

    // ── 富文本 ──

    fun getRichTexts(parentId: Long): Flow<List<RichText>> = dao.getRichTexts(parentId)

    suspend fun getRichTextsOnce(parentId: Long): List<RichText> = dao.getRichTextsOnce(parentId)

    suspend fun insertRichText(richText: RichText): Long = dao.insertRichText(richText)

    suspend fun updateRichText(richText: RichText) = dao.updateRichText(richText)

    suspend fun deleteRichText(richText: RichText) = dao.deleteRichText(richText)

    // ── 主线支线 ──

    suspend fun getBranches(parentId: Long): List<MainlineBranch> = dao.getBranches(parentId)

    suspend fun insertBranch(branch: MainlineBranch): Long = dao.insertBranch(branch)

    suspend fun updateBranch(branch: MainlineBranch) = dao.updateBranch(branch)

    suspend fun deleteBranch(branch: MainlineBranch) = dao.deleteBranch(branch)

    // ── 主线子项 ──

    suspend fun getBranchItems(branchId: Long): List<MainlineItem> = dao.getBranchItems(branchId)

    suspend fun insertBranchItem(item: MainlineItem): Long = dao.insertBranchItem(item)

    suspend fun updateBranchItem(item: MainlineItem) = dao.updateBranchItem(item)

    suspend fun deleteBranchItem(item: MainlineItem) = dao.deleteBranchItem(item)

    // ── 答题 ──

    fun getQuizGroups(parentId: Long): Flow<List<QuizGroup>> = dao.getQuizGroups(parentId)

    suspend fun getQuizGroupsOnce(parentId: Long): List<QuizGroup> = dao.getQuizGroupsOnce(parentId)

    suspend fun insertQuizGroup(group: QuizGroup): Long = dao.insertQuizGroup(group)

    suspend fun updateQuizGroup(group: QuizGroup) = dao.updateQuizGroup(group)

    suspend fun deleteQuizGroup(group: QuizGroup) = dao.deleteQuizGroup(group)

    suspend fun getQuizCards(groupId: Long): List<QuizCard> = dao.getQuizCards(groupId)

    suspend fun insertQuizCard(card: QuizCard): Long = dao.insertQuizCard(card)

    suspend fun updateQuizCard(card: QuizCard) = dao.updateQuizCard(card)

    suspend fun deleteQuizCard(card: QuizCard) = dao.deleteQuizCard(card)

    // ── 级联删除主列表 ──

    suspend fun deleteListCascade(id: Long) {
        dao.deleteOptionButtonsByParent(id)
        dao.deleteContentConfigsByParent(id)
        dao.deleteSubListsByParent(id)
        dao.deleteRichTextsByParent(id)
        dao.deleteQuizGroupsByParent(id)
        dao.deleteBranchesByParent(id)
        dao.deleteListById(id)
    }
}
