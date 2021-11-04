package com.example.covid_detect



import android.app.AlertDialog
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.text.Editable
import android.text.Html
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import java.io.*
import java.util.*


class HistoryActivity : AppCompatActivity() {


    @Suppress("SENSELESS_COMPARISON")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val colorDrawable = ColorDrawable(Color.parseColor("#000080"))
        supportActionBar?.setBackgroundDrawable(colorDrawable);
        supportActionBar?.title = Html.fromHtml("<font color='#FFFFFF'>History page</font>");

        //set the back "button"
        actionBar?.setDisplayHomeAsUpEnabled(true)

        setContentView(R.layout.activity_history)
        val empty = findViewById<TextView>(R.id.empty)
        empty.visibility = View.GONE

        var list : MutableList<String> = mutableListOf()
        list = findList()




        if(list.isEmpty()){
            empty.visibility = View.VISIBLE
        }
        else {
            //display the content of the file if is not empty
            val adapter = ArrayAdapter(this, R.layout.row, list)
            val listView: ListView = findViewById<ListView>(R.id.listView)
            listView.adapter = adapter
            listView.onItemClickListener = AdapterView.OnItemClickListener {
                    parent, view, position, id ->

                // remove clicked item from list view
                //val listItems = arrayOf("Delete", "Don't delete")
                val mBuilder = AlertDialog.Builder(this)
                mBuilder.setTitle("Delete this line?")

                var inputSelection =1

                //mBuilder.setSingleChoiceItems(listItems, -1) { _, i ->
                //    inputSelection = i
                //}

                mBuilder.setPositiveButton(
                    "Yes"
                ) { _, _ ->
                    //if (inputSelection==0){
                        Toast.makeText(this.applicationContext, "Deleted", Toast.LENGTH_SHORT).show()
                        removeLine(position)
                        list.removeAt(position)
                        if(list.isEmpty()) {empty.visibility = View.VISIBLE}
                        adapter.notifyDataSetChanged()
                   // }else { Toast.makeText(this.applicationContext, "Don't deleted", Toast.LENGTH_SHORT).show()}

                }
                mBuilder.setNegativeButton(
                    "No"
                ) { dialog, _ -> dialog.cancel() }

                val mDialog = mBuilder.create()
                mDialog.show()

            }
        }






    }


    private fun removeLine(pos : Int){

        val file = File(this.applicationContext!!.filesDir, "temp.txt")
        file.createNewFile()
        val f = PrintWriter(FileOutputStream(file, true))


        val file2 = File(this.applicationContext!!.filesDir, "History.txt")


        var riga : String?
        val reader = BufferedReader(FileReader(file2))
        riga = reader.readLine()
        var i = 0

        while (riga != null){
            if(i != pos){
                f.println(riga)
            }
            i++
            riga = reader.readLine()
        }

        reader.close()
        f.close()

        file2.delete()
        file.renameTo(File(this.applicationContext!!.filesDir,"History.txt"))



    }

    private fun findList(): MutableList<String> {
        var list : MutableList<String> = mutableListOf()

        val file = File(this.applicationContext!!.filesDir, "History.txt")
        if(file.exists()){

            var riga : String?
            val reader = BufferedReader(FileReader(file))
            riga = reader.readLine()

            while (riga != null){

                val st = StringTokenizer(riga, "*")
                var name = st.nextToken()
                var type = st.nextToken()
                list.add(name+type)

                riga = reader.readLine()
            }
            reader.close()
        }
        return list
    }

    override fun onSupportNavigateUp(): Boolean {
        //set the back "button" to resume the main activity and not to re create it
        finish()
        return true
    }

}
