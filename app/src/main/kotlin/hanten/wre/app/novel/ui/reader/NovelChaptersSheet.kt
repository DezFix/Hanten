package hanten.wre.app.novel.ui.reader

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.color.MaterialColors
import hanten.wre.app.databinding.ItemNovelChapterBinding
import hanten.wre.app.databinding.SheetNovelChaptersBinding
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import androidx.appcompat.R as appcompatR
import com.google.android.material.R as materialR

class NovelChaptersSheet : BottomSheetDialogFragment() {

	interface Callback {
		fun onChapterSelected(index: Int)

		/**
		 * Chapter titles come from the host screen. A novel can have thousands of
		 * chapters, and putting them into the fragment arguments overflowed the Binder
		 * buffer (TransactionTooLargeException) when the activity state was saved.
		 */
		fun observeChapterList(): Flow<ChapterList>
	}

	class ChapterList(
		val titles: List<String>,
		val downloaded: BooleanArray,
		val currentIndex: Int,
	)

	private var _binding: SheetNovelChaptersBinding? = null
	private val binding get() = _binding!!

	private var callback: Callback? = null

	override fun onCreateView(
		inflater: LayoutInflater,
		container: ViewGroup?,
		savedInstanceState: Bundle?,
	): View {
		_binding = SheetNovelChaptersBinding.inflate(inflater, container, false)
		return binding.root
	}

	override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
		super.onViewCreated(view, savedInstanceState)
		callback = activity as? Callback ?: parentFragment as? Callback
		binding.chaptersList.layoutManager = LinearLayoutManager(requireContext())

		val source = callback ?: run {
			dismissAllowingStateLoss()
			return
		}
		val adapter = ChapterAdapter { index ->
			dismiss()
			callback?.onChapterSelected(index)
		}
		binding.chaptersList.adapter = adapter
		val query = MutableStateFlow("")
		val downloadedOnly = MutableStateFlow(false)
		binding.editSearch.doAfterTextChanged { query.value = it?.toString().orEmpty().trim() }
		binding.chipDownloaded.setOnCheckedChangeListener { _, isChecked -> downloadedOnly.value = isChecked }
		var isScrolledToCurrent = false
		viewLifecycleOwner.lifecycleScope.launch {
			viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
				combine(source.observeChapterList(), query, downloadedOnly) { list, q, onlyDownloaded ->
					Triple(list, q, onlyDownloaded)
				}.collect { (list, q, onlyDownloaded) ->
					binding.chipDownloaded.isVisible = list.downloaded.any { it }
					// Search by title or by chapter number ("125" finds the 125th chapter).
					val number = q.toIntOrNull()
					val indices = list.titles.indices.filter { index ->
						(!onlyDownloaded || list.downloaded.getOrElse(index) { false }) &&
							(q.isEmpty() || list.titles[index].contains(q, ignoreCase = true) || number == index + 1)
					}
					binding.chaptersList.isVisible = indices.isNotEmpty()
					binding.textEmpty.isVisible = indices.isEmpty()
					// Update in place: replacing the adapter would jump the list back to the top.
					adapter.setData(list.titles, list.downloaded, list.currentIndex, indices)
					if (indices.isNotEmpty() && !isScrolledToCurrent && q.isEmpty() && !onlyDownloaded) {
						isScrolledToCurrent = true
						binding.chaptersList.scrollToPosition(
							indices.indexOf(list.currentIndex).coerceAtLeast(0),
						)
					}
				}
			}
		}
	}

	override fun onDestroyView() {
		super.onDestroyView()
		binding.chaptersList.adapter = null
		_binding = null
	}

	private class ChapterAdapter(
		private val onSelected: (Int) -> Unit,
	) : RecyclerView.Adapter<ChapterAdapter.ViewHolder>() {

		private var titles: List<String> = emptyList()
		private var downloaded: BooleanArray = BooleanArray(0)
		private var currentIndex: Int = -1
		private var indices: List<Int> = emptyList()

		@SuppressLint("NotifyDataSetChanged")
		fun setData(titles: List<String>, downloaded: BooleanArray, currentIndex: Int, indices: List<Int>) {
			this.titles = titles
			this.downloaded = downloaded
			this.currentIndex = currentIndex
			this.indices = indices
			notifyDataSetChanged()
		}

		class ViewHolder(val binding: ItemNovelChapterBinding) : RecyclerView.ViewHolder(binding.root)

		override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
			val inflater = LayoutInflater.from(parent.context)
			return ViewHolder(ItemNovelChapterBinding.inflate(inflater, parent, false))
		}

		override fun getItemCount(): Int = indices.size

		override fun onBindViewHolder(holder: ViewHolder, position: Int) {
			val chapterIndex = indices[position]
			val isCurrent = chapterIndex == currentIndex
			holder.binding.textChapterTitle.text = titles[chapterIndex]
			holder.binding.textChapterTitle.setTextColor(
				MaterialColors.getColor(
					holder.itemView,
					if (isCurrent) appcompatR.attr.colorPrimary else materialR.attr.colorOnSurface,
				),
			)
			holder.itemView.isSelected = isCurrent
			holder.binding.imageDownloaded.isVisible = downloaded.getOrElse(chapterIndex) { false }
			holder.itemView.setOnClickListener {
				val adapterPosition = holder.bindingAdapterPosition
				if (adapterPosition != RecyclerView.NO_POSITION) {
					onSelected(indices[adapterPosition])
				}
			}
		}
	}

	companion object {
		const val TAG = "NovelChaptersSheet"

		fun newInstance(): NovelChaptersSheet = NovelChaptersSheet()
	}
}
