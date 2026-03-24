package io.josrom.whereismycar

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import io.josrom.whereismycar.databinding.FragmentManageCategoriesBinding
import io.josrom.whereismycar.databinding.LayoutHeaderBinding

class ManageCategoriesFragment : Fragment() {

    private var _binding: FragmentManageCategoriesBinding? = null
    private val binding get() = _binding!!
    private lateinit var headerBinding: LayoutHeaderBinding
    private lateinit var repository: CategoryRepository
    private lateinit var adapter: ManageCategoriesAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentManageCategoriesBinding.inflate(inflater, container, false)
        headerBinding = LayoutHeaderBinding.bind(binding.layoutHeader.root)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        repository = CategoryRepository(requireContext())
        (activity as? MainActivity)?.let { mainActivity ->
            repository.setSyncManager(mainActivity.syncManager)
        }
        
        setupHeader()
        setupRecyclerView()
        
        binding.btnAddCategory.setOnClickListener {
            openEditCategoryFragment()
        }
    }

    private fun setupHeader() {
        headerBinding.tvToolbarTitle.text = getString(R.string.manage_categories)
        headerBinding.btnBack.setOnClickListener {
            findNavController().popBackStack()
        }
        headerBinding.btnToolbarActionIcon.apply {
            visibility = View.VISIBLE
            setImageResource(R.drawable.ic_search)
            setOnClickListener {
                // TODO: Implement search
            }
        }
    }

    private fun setupRecyclerView() {
        adapter = ManageCategoriesAdapter(
            categories = emptyList(),
            repository = repository,
            onEditClick = { category ->
                openEditCategoryFragment(category.id)
            },
            onDeleteClick = { category ->
                category.confirmAndDelete(requireContext(), repository) {
                    refreshCategories()
                }
            }
        )
        
        binding.rvCategories.layoutManager = LinearLayoutManager(requireContext())
        binding.rvCategories.adapter = adapter
        
        refreshCategories()
    }

    private fun refreshCategories() {
        val categories = repository.getAllCategories()
        adapter.updateData(categories)
    }

    private fun openEditCategoryFragment(categoryId: Int = -1) {
        val bundle = Bundle().apply {
            putInt("category_id", categoryId)
        }
        findNavController().navigate(R.id.action_manage_categories_fragment_to_edit_category_fragment, bundle)
    }

    override fun onResume() {
        super.onResume()
        refreshCategories()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
