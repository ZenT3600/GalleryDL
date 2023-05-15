package it.matteoleggio.gallerydl.ui.more

import android.content.Context
import android.content.DialogInterface
import android.graphics.Canvas
import android.graphics.Color
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.widget.RelativeLayout
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import it.matteoleggio.gallerydl.R
import it.matteoleggio.gallerydl.adapter.TemplatesAdapter
import it.matteoleggio.gallerydl.database.models.CommandTemplate
import it.matteoleggio.gallerydl.database.viewmodel.CommandTemplateViewModel
import it.matteoleggio.gallerydl.ui.BaseActivity
import it.matteoleggio.gallerydl.util.FileUtil
import it.matteoleggio.gallerydl.util.UiUtil
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.chip.Chip
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import it.xabaras.android.recyclerview.swipedecorator.RecyclerViewSwipeDecorator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext


class CommandTemplatesActivity : BaseActivity(), TemplatesAdapter.OnItemClickListener {
    private lateinit var recyclerView: RecyclerView
    private lateinit var templatesAdapter: TemplatesAdapter
    private lateinit var topAppBar: MaterialToolbar
    private lateinit var commandTemplateViewModel: CommandTemplateViewModel
    private lateinit var uiUtil: UiUtil
    private lateinit var templatesList: List<CommandTemplate>
    private lateinit var noResults: RelativeLayout
    var context: Context? = null

    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_command_templates)
        context = baseContext
        templatesList = listOf()

        topAppBar = findViewById(R.id.logs_toolbar)
        topAppBar.setNavigationOnClickListener { onBackPressedDispatcher.onBackPressed() }
        noResults = findViewById(R.id.no_results)

        templatesAdapter =
            TemplatesAdapter(
                this,
                this@CommandTemplatesActivity
            )
        recyclerView = findViewById(R.id.template_recyclerview)
        recyclerView.layoutManager = LinearLayoutManager(context)
        recyclerView.adapter = templatesAdapter
        val itemTouchHelper = ItemTouchHelper(simpleCallback)
        itemTouchHelper.attachToRecyclerView(recyclerView)

        uiUtil = UiUtil(FileUtil())

        commandTemplateViewModel = ViewModelProvider(this)[CommandTemplateViewModel::class.java]
        commandTemplateViewModel.items.observe(this) {
            if (it.isEmpty()) noResults.visibility = View.VISIBLE
            else noResults.visibility = View.GONE
            templatesList = it
            templatesAdapter.submitList(it)
        }
        initMenu()
        initChips()
    }

    private fun initMenu() {
        topAppBar.setOnMenuItemClickListener { m: MenuItem ->
            val itemId = m.itemId
            if (itemId == R.id.export_clipboard) {
                lifecycleScope.launch{
                    withContext(Dispatchers.IO){
                        commandTemplateViewModel.exportToClipboard()
                    }
                    Snackbar.make(recyclerView, getString(R.string.copied_to_clipboard), Snackbar.LENGTH_LONG).show()
                }
            }else if (itemId == R.id.import_clipboard){
                lifecycleScope.launch{
                    withContext(Dispatchers.IO){
                        val count = commandTemplateViewModel.importFromClipboard()
                        runOnUiThread{
                            Snackbar.make(recyclerView, "${getString(R.string.items_imported)} (${count})", Snackbar.LENGTH_LONG).show()
                        }
                    }

                }
            }
            true
        }
    }

    private fun initChips() {
        val new = findViewById<Chip>(R.id.newTemplate)
        new.setOnClickListener {
            uiUtil.showCommandTemplateCreationOrUpdatingSheet(null,this@CommandTemplatesActivity, this, commandTemplateViewModel) {}
        }
        val shortcuts = findViewById<Chip>(R.id.shortcuts)
        shortcuts.setOnClickListener {
            uiUtil.showShortcutsSheet(this@CommandTemplatesActivity,this, commandTemplateViewModel)
        }
    }

    companion object {
        private const val TAG = "DownloadLogActivity"
    }

    override fun onItemClick(commandTemplate: CommandTemplate, index: Int) {
        uiUtil.showCommandTemplateCreationOrUpdatingSheet(commandTemplate,this@CommandTemplatesActivity, this, commandTemplateViewModel) {
            templatesAdapter.notifyItemChanged(index)
        }

    }

    override fun onSelected(commandTemplate: CommandTemplate) {
        TODO("Not yet implemented")
    }

    override fun onDelete(commandTemplate: CommandTemplate) {
        val deleteDialog = MaterialAlertDialogBuilder(this)
        deleteDialog.setTitle(getString(R.string.you_are_going_to_delete) + " \"" + commandTemplate.title + "\"!")
        deleteDialog.setNegativeButton(getString(R.string.cancel)) { dialogInterface: DialogInterface, _: Int -> dialogInterface.cancel() }
        deleteDialog.setPositiveButton(getString(R.string.ok)) { _: DialogInterface?, _: Int ->
            commandTemplateViewModel.delete(commandTemplate)
        }
        deleteDialog.show()
    }

    private var simpleCallback: ItemTouchHelper.SimpleCallback =
        object : ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT ) {
            override fun onMove(recyclerView: RecyclerView,viewHolder: RecyclerView.ViewHolder,target: RecyclerView.ViewHolder
            ): Boolean {
                return false
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                val position = viewHolder.bindingAdapterPosition
                when (direction) {
                    ItemTouchHelper.LEFT -> {
                        val deletedItem = templatesList[position]
                        templatesAdapter.notifyItemChanged(position)
                        onDelete(deletedItem)
                    }

                }
            }

            override fun onChildDraw(
                c: Canvas,
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                dX: Float,
                dY: Float,
                actionState: Int,
                isCurrentlyActive: Boolean
            ) {
                RecyclerViewSwipeDecorator.Builder(
                    context,
                    c,
                    recyclerView,
                    viewHolder,
                    dX,
                    dY,
                    actionState,
                    isCurrentlyActive
                )
                    .addSwipeLeftBackgroundColor(Color.RED)
                    .addSwipeLeftActionIcon(R.drawable.baseline_delete_24)
                    .create()
                    .decorate()
                super.onChildDraw(
                    c,
                    recyclerView,
                    viewHolder,
                    dX,
                    dY,
                    actionState,
                    isCurrentlyActive
                )
            }
        }
}