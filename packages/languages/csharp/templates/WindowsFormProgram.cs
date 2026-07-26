using System;
using System.Drawing;
using System.Windows.Forms;

public static class ${CLASS_NAME}
{
    [STAThread]
    public static void Main()
    {
        Application.EnableVisualStyles();
        Application.SetCompatibleTextRenderingDefault(false);

        Form window = new()
        {
            Text = "${FILE_TITLE}",
            ClientSize = new Size(320, 140),
            StartPosition = FormStartPosition.CenterScreen
        };

        Button button = new()
        {
            Text = "Open Hello Window",
            AutoSize = true,
            Location = new Point(80, 50)
        };
        button.Click += (_, _) =>
                MessageBox.Show(
                        window,
                        "Hello world.",
                        "Hello",
                        MessageBoxButtons.OK,
                        MessageBoxIcon.Information
                );
        window.Controls.Add(button);
        Application.Run(window);
    }
}
