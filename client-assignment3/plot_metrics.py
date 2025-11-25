import pandas as pd
import matplotlib.pyplot as plt
import sys
import os

def plot_metrics(file_name, component_name):
    print(f"--- Processing {file_name} ---")

    if not os.path.exists(file_name):
        print(f"❌ Error: File '{file_name}' not found.")
        return

    try:
        # Read CSV
        df = pd.read_csv(file_name)

        # Convert Timestamp string to datetime for better plotting if needed,
        # or just use index/string as x-axis. Using string directly is simpler for labels.

        # Setup the plot with 2 subplots sharing x-axis
        fig, (ax1, ax2) = plt.subplots(2, 1, figsize=(12, 10), sharex=True)

        # --- Subplot 1: Memory Usage ---
        ax1.plot(df['Timestamp'], df['Memory_MB'], color='blue', linewidth=2, label='Memory (MB)')
        ax1.set_title(f'{component_name} - Memory Usage (Endurance Test)', fontsize=14)
        ax1.set_ylabel('Memory (MB)', fontsize=12)
        ax1.grid(True, linestyle='--', alpha=0.7)
        ax1.legend(loc='upper left')

        # --- Subplot 2: Connections OR Disk Usage ---
        # We decide what to plot based on the data available
        # If connections are present (non-zero max), plot connections (RabbitMQ/Mongo)
        # Otherwise plot Disk Usage (Consumer/Server logs)

        max_conns = df['Connections'].max()

        if max_conns > 0 or component_name in ["RabbitMQ", "MongoDB"]:
            # Plot Connections
            ax2.plot(df['Timestamp'], df['Connections'], color='green', linewidth=2, label='Active Connections')
            ax2.set_title(f'{component_name} - Network Connections', fontsize=14)
            ax2.set_ylabel('Count', fontsize=12)
            ax2.legend(loc='upper left')
        else:
            # Plot Disk Usage
            ax2.plot(df['Timestamp'], df['Disk_Usage_%'], color='red', linewidth=2, label='Disk Usage %')
            ax2.set_title(f'{component_name} - Disk Space Usage', fontsize=14)
            ax2.set_ylabel('% Used', fontsize=12)
            ax2.set_ylim(0, 100) # Fix y-axis to 0-100%
            ax2.legend(loc='upper left')

        ax2.grid(True, linestyle='--', alpha=0.7)

        # X-Axis Formatting
        # Show only every Nth label to avoid clutter (e.g., every 10 minutes/entries)
        n = max(1, len(df) // 10)
        plt.xticks(df['Timestamp'][::n], rotation=45)
        plt.xlabel('Time (HH:MM:SS)', fontsize=12)

        plt.tight_layout()

        # Save
        output_file = file_name.replace('.csv', '_graph.png')
        plt.savefig(output_file)
        print(f"✅ Saved Graph: {output_file}")
        plt.close()

    except Exception as e:
        print(f"❌ Error plotting {file_name}: {e}")

if __name__ == "__main__":
    if len(sys.argv) < 3:
        print("Usage: python3 plot_metrics.py <csv_file> <Component Name>")
        print("Example: python3 plot_metrics.py endurance_mongo.csv MongoDB")
    else:
        plot_metrics(sys.argv[1], sys.argv[2])